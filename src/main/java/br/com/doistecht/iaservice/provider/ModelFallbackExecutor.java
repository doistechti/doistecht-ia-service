package br.com.doistecht.iaservice.provider;

import br.com.doistecht.iaservice.provider.AiProviderException.Reason;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executa chamadas a um provedor com retry, circuit breaker e modelo reserva (fallback).
 * <p>
 * Cada modelo tem seu próprio circuit breaker e retry (configurações padrão em
 * {@code resilience4j.*.configs.default}). O fluxo de uma chamada é:
 * <ol>
 * <li>tenta o modelo principal, com novas tentativas para erros transitórios;</li>
 * <li>se o modelo principal continuar falhando por erro transitório, ou se o circuito dele
 * estiver aberto, tenta o modelo reserva do mesmo jeito;</li>
 * <li>se nada der certo, lança {@link AiProviderException}.</li>
 * </ol>
 * Erros permanentes (ex.: requisição inválida) não são retentados, não abrem o circuito
 * e não acionam o fallback, pois falhariam do mesmo jeito.
 */
public class ModelFallbackExecutor {

	private static final Logger log = LoggerFactory.getLogger(ModelFallbackExecutor.class);

	private final String provider;

	private final String primaryModel;

	private final String fallbackModel;

	private final Predicate<Throwable> isTransient;

	private final Guard primaryGuard;

	private final Guard fallbackGuard;

	/** Resultado de uma execução e de qual modelo ele veio. */
	public record Outcome<T>(T value, String model, boolean fallback) {
	}

	/**
	 * @param fallbackModel modelo reserva; {@code null} ou igual ao principal desativa o fallback
	 * @param isTransient   diz se um erro do provedor é transitório (vale retentar)
	 */
	public ModelFallbackExecutor(String provider, String primaryModel, String fallbackModel,
			Predicate<Throwable> isTransient, CircuitBreakerRegistry circuitBreakers, RetryRegistry retries) {
		this.provider = provider;
		this.primaryModel = primaryModel;
		this.fallbackModel = fallbackModel == null || fallbackModel.isBlank() || fallbackModel.equals(primaryModel)
				? null
				: fallbackModel;
		this.isTransient = isTransient;
		this.primaryGuard = new Guard(primaryModel, circuitBreakers, retries);
		this.fallbackGuard = this.fallbackModel == null ? null : new Guard(this.fallbackModel, circuitBreakers, retries);
	}

	public String primaryModel() {
		return primaryModel;
	}

	/**
	 * @param call chamada ao provedor, que recebe o nome do modelo a usar
	 */
	public <T> Outcome<T> execute(Function<String, T> call) {
		try {
			return new Outcome<>(primaryGuard.run(() -> call.apply(primaryModel)), primaryModel, false);
		}
		catch (RuntimeException primaryFailure) {
			if (fallbackGuard == null || !isRecoverable(primaryFailure)) {
				throw logged(toProviderException(primaryFailure, primaryGuard), primaryModel);
			}
			log.warn("Modelo {} indisponível ({}); usando o modelo reserva {}", primaryModel,
					describe(primaryFailure), fallbackModel);
			try {
				return new Outcome<>(fallbackGuard.run(() -> call.apply(fallbackModel)), fallbackModel, true);
			}
			catch (RuntimeException fallbackFailure) {
				fallbackFailure.addSuppressed(primaryFailure);
				throw logged(toProviderException(fallbackFailure, fallbackGuard), fallbackModel);
			}
		}
	}

	/** Converte um erro do provedor na exceção padrão, classificando se é transitório. */
	public AiProviderException toProviderException(Throwable failure) {
		return toProviderException(failure, primaryGuard);
	}

	private AiProviderException toProviderException(Throwable failure, Guard guard) {
		if (failure instanceof AiProviderException providerException) {
			return providerException;
		}
		if (failure instanceof CallNotPermittedException) {
			return new AiProviderException(provider, Reason.UNAVAILABLE,
					"Provedor " + provider + " temporariamente indisponível (circuit breaker aberto)",
					guard.secondsUntilHalfOpen(), failure);
		}
		if (isTransient.test(failure)) {
			return new AiProviderException(provider, Reason.UNAVAILABLE,
					"Provedor " + provider + " indisponível após novas tentativas", null, failure);
		}
		return new AiProviderException(provider, Reason.FAILED, "Falha ao chamar o provedor " + provider, null,
				failure);
	}

	private static AiProviderException logged(AiProviderException exception, String model) {
		// Circuito aberto já foi registrado quando abriu; aqui só geraria ruído a cada requisição
		if (exception.getCause() instanceof CallNotPermittedException) {
			log.debug("Chamada ao modelo {} recusada: circuit breaker aberto", model);
		}
		else {
			log.error("Falha ao chamar o modelo {} ({})", model, exception.getReason(), exception);
		}
		return exception;
	}

	private boolean isRecoverable(Throwable failure) {
		return failure instanceof CallNotPermittedException || isTransient.test(failure);
	}

	private static String describe(Throwable failure) {
		return failure instanceof CallNotPermittedException ? "circuit breaker aberto" : failure.toString();
	}

	/** Circuit breaker e retry de um modelo. */
	private final class Guard {

		private final CircuitBreaker circuitBreaker;

		private final Retry retry;

		Guard(String model, CircuitBreakerRegistry circuitBreakers, RetryRegistry retries) {
			String name = provider + ":" + model;
			// Só erros transitórios contam: um prompt inválido não deve abrir o circuito
			this.circuitBreaker = circuitBreakers.circuitBreaker(name,
					CircuitBreakerConfig.from(circuitBreakers.getDefaultConfig())
							.recordException(isTransient)
							.build());
			// Circuito aberto (CallNotPermittedException) não é transitório: não retenta
			this.retry = retries.retry(name,
					RetryConfig.from(retries.getDefaultConfig())
							.retryOnException(isTransient)
							.build());
		}

		<T> T run(Supplier<T> call) {
			// Retry por fora: cada tentativa passa pelo circuit breaker e conta na estatística dele
			return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call)).get();
		}

		Long secondsUntilHalfOpen() {
			long millis = circuitBreaker.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1);
			return Math.max(1, Duration.ofMillis(millis).toSeconds());
		}

	}

}

package br.com.doistecht.iaservice.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.doistecht.iaservice.provider.AiProviderException.Reason;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class ModelFallbackExecutorTest {

	private static final String PRIMARY = "modelo-principal";

	private static final String FALLBACK = "modelo-reserva";

	/** Chamadas feitas, na ordem, com o modelo usado em cada uma. */
	private final List<String> calls = new ArrayList<>();

	private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
			.slidingWindowSize(4)
			.minimumNumberOfCalls(4)
			.failureRateThreshold(50)
			.waitDurationInOpenState(Duration.ofSeconds(60))
			.build());

	private final RetryRegistry retries = RetryRegistry.of(RetryConfig.custom()
			.maxAttempts(3)
			.waitDuration(Duration.ofMillis(1))
			.build());

	private ModelFallbackExecutor executor(String fallbackModel) {
		return new ModelFallbackExecutor("teste", PRIMARY, fallbackModel, TransientFailure.class::isInstance,
				circuitBreakers, retries);
	}

	@Test
	void shouldReturnPrimaryResultWhenCallSucceeds() {
		var outcome = executor(FALLBACK).execute(record(model -> "ok:" + model));

		assertThat(outcome.value()).isEqualTo("ok:" + PRIMARY);
		assertThat(outcome.fallback()).isFalse();
		assertThat(calls).containsExactly(PRIMARY);
	}

	@Test
	void shouldRetryTransientFailures() {
		var outcome = executor(FALLBACK).execute(record(model -> {
			if (calls.size() < 3) {
				throw new TransientFailure();
			}
			return "ok";
		}));

		assertThat(outcome.fallback()).isFalse();
		assertThat(calls).containsExactly(PRIMARY, PRIMARY, PRIMARY);
	}

	@Test
	void shouldUseFallbackModelWhenPrimaryKeepsFailing() {
		var outcome = executor(FALLBACK).execute(record(model -> {
			if (model.equals(PRIMARY)) {
				throw new TransientFailure();
			}
			return "ok:" + model;
		}));

		assertThat(outcome.value()).isEqualTo("ok:" + FALLBACK);
		assertThat(outcome.fallback()).isTrue();
		assertThat(outcome.model()).isEqualTo(FALLBACK);
		assertThat(calls).containsExactly(PRIMARY, PRIMARY, PRIMARY, FALLBACK);
	}

	@Test
	void shouldNotRetryNorFallbackOnPermanentFailure() {
		assertThatThrownBy(() -> executor(FALLBACK).execute(record(model -> {
			throw new IllegalArgumentException("prompt inválido");
		})))
				.isInstanceOf(AiProviderException.class)
				.extracting(ex -> ((AiProviderException) ex).getReason())
				.isEqualTo(Reason.FAILED);

		assertThat(calls).containsExactly(PRIMARY);
	}

	@Test
	void shouldReportUnavailableWhenFallbackAlsoFails() {
		assertThatThrownBy(() -> executor(FALLBACK).execute(record(model -> {
			throw new TransientFailure();
		})))
				.isInstanceOf(AiProviderException.class)
				.extracting(ex -> ((AiProviderException) ex).getReason())
				.isEqualTo(Reason.UNAVAILABLE);

		assertThat(calls).hasSize(6);
	}

	@Test
	void shouldSkipPrimaryModelWhileItsCircuitIsOpen() {
		ModelFallbackExecutor executor = executor(FALLBACK);
		Function<String, String> primaryDown = model -> {
			if (model.equals(PRIMARY)) {
				throw new TransientFailure();
			}
			return "ok";
		};

		// 2 execuções x 3 tentativas: janela de 4 chamadas com 100% de falha abre o circuito
		executor.execute(record(primaryDown));
		executor.execute(record(primaryDown));
		calls.clear();

		var outcome = executor.execute(record(primaryDown));

		assertThat(outcome.fallback()).isTrue();
		assertThat(calls).containsExactly(FALLBACK);
	}

	@Test
	void shouldSuggestRetryAfterWhenCircuitIsOpenAndThereIsNoFallback() {
		ModelFallbackExecutor executor = executor(null);
		Function<String, String> down = model -> {
			throw new TransientFailure();
		};
		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> executor.execute(record(down))).isInstanceOf(AiProviderException.class);
		}

		assertThatThrownBy(() -> executor.execute(record(down)))
				.isInstanceOfSatisfying(AiProviderException.class, ex -> {
					assertThat(ex.getReason()).isEqualTo(Reason.UNAVAILABLE);
					assertThat(ex.getRetryAfterSeconds()).isEqualTo(60L);
				});
	}

	@Test
	void shouldIgnoreFallbackEqualToPrimary() {
		assertThatThrownBy(() -> executor(PRIMARY).execute(record(model -> {
			throw new TransientFailure();
		}))).isInstanceOf(AiProviderException.class);

		assertThat(calls).containsOnly(PRIMARY).hasSize(3);
	}

	@Test
	void shouldStreamFromFallbackWhenPrimaryFailsBeforeFirstChunk() {
		List<String> chunks = executor(FALLBACK).stream(model -> {
			calls.add(model);
			return model.equals(PRIMARY)
					? Flux.<String>error(new TransientFailure())
					: Flux.just("a", "b");
		}).collectList().block();

		assertThat(chunks).containsExactly("a", "b");
		assertThat(calls).containsExactly(PRIMARY, FALLBACK);
	}

	@Test
	void shouldNotSwitchModelAfterFirstChunk() {
		Flux<String> stream = executor(FALLBACK).stream(model -> {
			calls.add(model);
			return Flux.concat(Flux.just("parte"), Flux.error(new TransientFailure()));
		});

		assertThatThrownBy(() -> stream.collectList().block()).isInstanceOf(TransientFailure.class);
		assertThat(calls).containsExactly(PRIMARY);
	}

	@Test
	void shouldStartStreamWithFallbackWhenPrimaryCircuitIsOpen() {
		ModelFallbackExecutor executor = executor(FALLBACK);
		Function<String, String> primaryDown = model -> {
			if (model.equals(PRIMARY)) {
				throw new TransientFailure();
			}
			return "ok";
		};
		executor.execute(record(primaryDown));
		executor.execute(record(primaryDown));
		calls.clear();

		executor.stream(model -> {
			calls.add(model);
			return Flux.just("ok");
		}).blockLast();

		assertThat(calls).containsExactly(FALLBACK);
	}

	private <T> Function<String, T> record(Function<String, T> call) {
		return model -> {
			calls.add(model);
			return call.apply(model);
		};
	}

	private static final class TransientFailure extends RuntimeException {

		TransientFailure() {
			super("503 temporário");
		}

	}

}

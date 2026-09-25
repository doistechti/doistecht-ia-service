package br.com.doistecht.iaservice.gateway;

import br.com.doistecht.iaservice.gateway.ProviderRouter.Selection;
import br.com.doistecht.iaservice.metrics.GatewayMetrics;
import br.com.doistecht.iaservice.metrics.GatewayMetrics.Outcome;
import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.EmbeddingPurpose;
import br.com.doistecht.iaservice.provider.EmbeddingResult;
import br.com.doistecht.iaservice.provider.ModelProvider;
import br.com.doistecht.iaservice.provider.StreamChunk;
import br.com.doistecht.iaservice.provider.TokenUsage;
import br.com.doistecht.iaservice.structured.JsonSchemaValidator;
import br.com.doistecht.iaservice.usage.UsageEvent;
import br.com.doistecht.iaservice.usage.UsageEvent.Operation;
import br.com.doistecht.iaservice.usage.UsageRecorder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import tools.jackson.databind.ObjectMapper;

/**
 * Implementação de {@link AiProvider} usada por toda a aplicação. Para cada chamada:
 * <ul>
 * <li><b>escolhe o provedor</b> com o {@link ProviderRouter};</li>
 * <li>usa o <b>cache</b> de respostas por cliente (exceto streaming e embeddings);</li>
 * <li>troca para o <b>provedor reserva</b> se o escolhido estiver fora do ar;</li>
 * <li><b>registra o uso</b> (tokens, latência, custo, cache) e as <b>métricas</b>.</li>
 * </ul>
 */
@Primary
@Component
public class GatewayAiProvider implements AiProvider {

	private static final Logger log = LoggerFactory.getLogger(GatewayAiProvider.class);

	private final ProviderRouter router;

	private final ResponseCache cache;

	private final UsageRecorder usageRecorder;

	private final JsonSchemaValidator schemaValidator;

	private final ObjectMapper objectMapper;

	private final GatewayMetrics metrics;

	public GatewayAiProvider(ProviderRouter router, ResponseCache cache, UsageRecorder usageRecorder,
			JsonSchemaValidator schemaValidator, ObjectMapper objectMapper, GatewayMetrics metrics) {
		this.router = router;
		this.cache = cache;
		this.usageRecorder = usageRecorder;
		this.schemaValidator = schemaValidator;
		this.objectMapper = objectMapper;
		this.metrics = metrics;
	}

	@Override
	public ChatResult chat(ChatCommand command) {
		return execute(Operation.CHAT, command, null, provider -> provider.chat(command), result -> true);
	}

	@Override
	public ChatResult structured(ChatCommand command, String jsonSchema) {
		// Só guarda respostas que seguem o schema: uma resposta inválida em cache
		// faria a nova tentativa do StructuredOutputService receber o mesmo erro
		return execute(Operation.STRUCTURED, command, jsonSchema, provider -> provider.structured(command, jsonSchema),
				result -> matchesSchema(result.content(), jsonSchema));
	}

	// O provedor reserva só entra se a falha acontecer antes do primeiro trecho:
	// depois disso o cliente já recebeu parte da resposta
	@Override
	public Flux<StreamChunk> chatStream(ChatCommand command) {
		CallContext context = CallContext.current();
		Selection selection = router.select(command.provider(), context.clientDefaultProvider());
		long start = System.nanoTime();
		AtomicReference<String> provider = new AtomicReference<>(selection.primary().name());
		AtomicReference<String> model = new AtomicReference<>();
		AtomicReference<TokenUsage> usage = new AtomicReference<>();
		AtomicBoolean emitted = new AtomicBoolean();
		AtomicBoolean fallback = new AtomicBoolean();

		Flux<StreamChunk> stream = selection.primary().chatStream(command).doOnNext(chunk -> emitted.set(true));
		if (selection.fallback() != null) {
			ModelProvider reserve = selection.fallback();
			stream = stream.onErrorResume(ex -> !emitted.get() && isUnavailable(ex), ex -> {
				log.warn("Streaming no provedor {} falhou antes do primeiro trecho; usando o provedor reserva {}",
						provider.get(), reserve.name());
				record(context, Operation.STREAM, provider.get(), null, null, start, false, false, false);
				provider.set(reserve.name());
				fallback.set(true);
				return reserve.chatStream(command);
			});
		}

		return stream
				.doOnNext(chunk -> {
					if (chunk.model() != null) {
						model.set(chunk.model());
					}
					if (chunk.usage() != null) {
						usage.set(chunk.usage());
					}
				})
				// Registra ao terminar, falhar ou ser cancelado (cliente fechou a conexão)
				.doFinally(signal -> record(context, Operation.STREAM, provider.get(), model.get(), usage.get(), start,
						false, signal != SignalType.ON_ERROR, fallback.get()));
	}

	@Override
	public EmbeddingResult embed(List<String> texts, EmbeddingPurpose purpose, String requestedProvider) {
		CallContext context = CallContext.current();
		ModelProvider provider = router.embeddingProvider(requestedProvider);
		long start = System.nanoTime();
		try {
			EmbeddingResult result = provider.embed(texts, purpose);
			record(context, Operation.EMBEDDING, provider.name(), result.model(), result.usage(), start, false, true,
					false);
			return result;
		}
		catch (RuntimeException ex) {
			record(context, Operation.EMBEDDING, provider.name(), null, null, start, false, false, false);
			throw ex;
		}
	}

	private ChatResult execute(Operation operation, ChatCommand command, String jsonSchema,
			Function<ModelProvider, ChatResult> call, Predicate<ChatResult> cacheable) {
		CallContext context = CallContext.current();
		Selection selection = router.select(command.provider(), context.clientDefaultProvider());
		ModelProvider provider = selection.primary();
		long start = System.nanoTime();
		String key = cache.key(context.clientId(), operation.value(), provider.name(), command, jsonSchema);

		if (!context.bypassCache()) {
			Optional<ChatResult> cached = cache.get(key);
			if (cached.isPresent()) {
				record(context, operation, provider.name(), cached.get().model(), null, start, true, true, false);
				return cached.get();
			}
		}

		ChatResult result;
		try {
			result = call.apply(provider);
		}
		catch (RuntimeException ex) {
			record(context, operation, provider.name(), null, null, start, false, false, false);
			if (selection.fallback() == null || !isUnavailable(ex)) {
				throw ex;
			}
			result = callFallback(context, operation, selection.fallback(), call, provider.name());
		}
		record(context, operation, result.provider(), result.model(), result.usage(), start, false, true,
				result.fallback());
		// Respostas de reserva não vão para o cache: depois que o principal voltar,
		// os clientes não devem continuar recebendo a resposta do modelo reserva
		if (!result.fallback() && cacheable.test(result)) {
			cache.put(key, result);
		}
		return result;
	}

	private ChatResult callFallback(CallContext context, Operation operation, ModelProvider fallback,
			Function<ModelProvider, ChatResult> call, String failedProvider) {
		log.warn("Provedor {} indisponível; usando o provedor reserva {}", failedProvider, fallback.name());
		long start = System.nanoTime();
		try {
			return call.apply(fallback).asFallback();
		}
		catch (RuntimeException ex) {
			record(context, operation, fallback.name(), null, null, start, false, false, true);
			throw ex;
		}
	}

	// Só troca de provedor quando o problema é disponibilidade; um pedido inválido falharia igual no outro
	private static boolean isUnavailable(Throwable ex) {
		return ex instanceof AiProviderException providerException
				&& providerException.getReason() == AiProviderException.Reason.UNAVAILABLE;
	}

	private boolean matchesSchema(String content, String jsonSchema) {
		if (content == null || content.isBlank()) {
			return false;
		}
		try {
			var schema = schemaValidator.compile(objectMapper.readTree(jsonSchema));
			return schemaValidator.validate(schema, objectMapper.readTree(content)).isEmpty();
		}
		catch (RuntimeException ex) {
			return false;
		}
	}

	private void record(CallContext context, Operation operation, String provider, String model, TokenUsage usage,
			long startNanos, boolean cacheHit, boolean success, boolean fallback) {
		long latencyNanos = System.nanoTime() - startNanos;
		Outcome outcome = cacheHit ? Outcome.CACHE_HIT : success ? Outcome.SUCCESS : Outcome.FAILURE;
		metrics.recordCall(context.clientName(), operation.value(), provider, outcome, fallback,
				Duration.ofNanos(latencyNanos), usage == null ? null : usage.promptTokens(),
				usage == null ? null : usage.outputTokens());

		// Chamadas fora de uma requisição de cliente (ex.: tarefas internas) não são contabilizadas
		if (context.clientId() == null) {
			return;
		}
		usageRecorder.record(new UsageEvent(context.clientId(), context.endpoint(), operation, provider, model, usage,
				TimeUnit.NANOSECONDS.toMillis(latencyNanos), cacheHit, success, Instant.now()));
	}

}

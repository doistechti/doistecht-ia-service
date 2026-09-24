package br.com.doistecht.iaservice.gateway;

import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.EmbeddingPurpose;
import br.com.doistecht.iaservice.provider.EmbeddingResult;
import br.com.doistecht.iaservice.provider.StreamChunk;
import br.com.doistecht.iaservice.provider.TokenUsage;
import br.com.doistecht.iaservice.provider.gemini.GeminiProvider;
import br.com.doistecht.iaservice.metrics.GatewayMetrics;
import br.com.doistecht.iaservice.metrics.GatewayMetrics.Outcome;
import br.com.doistecht.iaservice.structured.JsonSchemaValidator;
import br.com.doistecht.iaservice.usage.UsageEvent;
import br.com.doistecht.iaservice.usage.UsageEvent.Operation;
import br.com.doistecht.iaservice.usage.UsageRecorder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import tools.jackson.databind.ObjectMapper;

/**
 * Decorator que envolve o provedor real e adiciona, sem que os chamadores percebam:
 * <ul>
 * <li><b>cache</b> de respostas por cliente (exceto streaming);</li>
 * <li><b>registro de uso</b> de cada chamada: tokens, latência, custo e se veio do cache;</li>
 * <li><b>métricas</b> para o Prometheus.</li>
 * </ul>
 * Por ser {@code @Primary}, é ele que controllers e serviços recebem ao pedir um {@link AiProvider}.
 */
@Primary
@Component
public class GatewayAiProvider implements AiProvider {

	private final AiProvider delegate;

	private final ResponseCache cache;

	private final UsageRecorder usageRecorder;

	private final JsonSchemaValidator schemaValidator;

	private final ObjectMapper objectMapper;

	private final GatewayMetrics metrics;

	// Na fase 6, o provedor concreto passa a ser escolhido por um roteador
	public GatewayAiProvider(GeminiProvider delegate, ResponseCache cache, UsageRecorder usageRecorder,
			JsonSchemaValidator schemaValidator, ObjectMapper objectMapper, GatewayMetrics metrics) {
		this.delegate = delegate;
		this.cache = cache;
		this.usageRecorder = usageRecorder;
		this.schemaValidator = schemaValidator;
		this.objectMapper = objectMapper;
		this.metrics = metrics;
	}

	@Override
	public String name() {
		return delegate.name();
	}

	@Override
	public ChatResult chat(ChatCommand command) {
		return execute(Operation.CHAT, command, null, () -> delegate.chat(command), result -> true);
	}

	@Override
	public ChatResult structured(ChatCommand command, String jsonSchema) {
		// Só guarda respostas que seguem o schema: uma resposta inválida em cache
		// faria a nova tentativa do StructuredOutputService receber o mesmo erro
		return execute(Operation.STRUCTURED, command, jsonSchema, () -> delegate.structured(command, jsonSchema),
				result -> matchesSchema(result.content(), jsonSchema));
	}

	@Override
	public Flux<StreamChunk> chatStream(ChatCommand command) {
		CallContext context = CallContext.current();
		long start = System.nanoTime();
		AtomicReference<String> model = new AtomicReference<>();
		AtomicReference<TokenUsage> usage = new AtomicReference<>();

		return delegate.chatStream(command)
				.doOnNext(chunk -> {
					if (chunk.model() != null) {
						model.set(chunk.model());
					}
					if (chunk.usage() != null) {
						usage.set(chunk.usage());
					}
				})
				// Registra ao terminar, falhar ou ser cancelado (cliente fechou a conexão)
				.doFinally(signal -> record(context, Operation.STREAM, model.get(), usage.get(), start, false,
						signal != SignalType.ON_ERROR, false));
	}

	@Override
	public EmbeddingResult embed(List<String> texts, EmbeddingPurpose purpose) {
		CallContext context = CallContext.current();
		long start = System.nanoTime();
		try {
			EmbeddingResult result = delegate.embed(texts, purpose);
			record(context, Operation.EMBEDDING, result.model(), result.usage(), start, false, true, false);
			return result;
		}
		catch (RuntimeException ex) {
			record(context, Operation.EMBEDDING, null, null, start, false, false, false);
			throw ex;
		}
	}

	private ChatResult execute(Operation operation, ChatCommand command, String jsonSchema,
			Supplier<ChatResult> call, Predicate<ChatResult> cacheable) {
		CallContext context = CallContext.current();
		long start = System.nanoTime();
		String key = cache.key(context.clientId(), operation.value(), delegate.name(), command, jsonSchema);

		if (!context.bypassCache()) {
			Optional<ChatResult> cached = cache.get(key);
			if (cached.isPresent()) {
				record(context, operation, cached.get().model(), null, start, true, true, false);
				return cached.get();
			}
		}

		ChatResult result;
		try {
			result = call.get();
		}
		catch (RuntimeException ex) {
			record(context, operation, null, null, start, false, false, false);
			throw ex;
		}
		record(context, operation, result.model(), result.usage(), start, false, true, result.fallback());
		// Respostas do modelo reserva não vão para o cache: depois que o principal voltar,
		// os clientes não devem continuar recebendo a resposta do modelo mais fraco
		if (!result.fallback() && cacheable.test(result)) {
			cache.put(key, result);
		}
		return result;
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

	private void record(CallContext context, Operation operation, String model, TokenUsage usage, long startNanos,
			boolean cacheHit, boolean success, boolean fallback) {
		long latencyNanos = System.nanoTime() - startNanos;
		Outcome outcome = cacheHit ? Outcome.CACHE_HIT : success ? Outcome.SUCCESS : Outcome.FAILURE;
		metrics.recordCall(context.clientName(), operation.value(), delegate.name(), outcome, fallback,
				Duration.ofNanos(latencyNanos), usage == null ? null : usage.promptTokens(),
				usage == null ? null : usage.outputTokens());

		// Chamadas fora de uma requisição de cliente (ex.: tarefas internas) não são contabilizadas
		if (context.clientId() == null) {
			return;
		}
		usageRecorder.record(new UsageEvent(context.clientId(), context.endpoint(), operation, delegate.name(), model,
				usage, TimeUnit.NANOSECONDS.toMillis(latencyNanos), cacheHit, success, Instant.now()));
	}

}

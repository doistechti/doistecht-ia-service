package br.com.doistecht.iaservice.provider.maritaca;

import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.EmbeddingPurpose;
import br.com.doistecht.iaservice.provider.EmbeddingResult;
import br.com.doistecht.iaservice.provider.ModelFallbackExecutor;
import br.com.doistecht.iaservice.provider.ModelProvider;
import br.com.doistecht.iaservice.provider.ProviderHealth;
import br.com.doistecht.iaservice.provider.ProviderInfo;
import br.com.doistecht.iaservice.provider.StreamChunk;
import br.com.doistecht.iaservice.provider.TokenUsage;
import br.com.doistecht.iaservice.provider.UnsupportedCapabilityException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * Provedor Maritaca: modelos Sabiá, treinados para o português do Brasil.
 * <p>
 * A API é compatível com a da OpenAI ({@code POST /chat/completions}) e é chamada direto por
 * HTTP, sem SDK. A Maritaca não oferece embeddings: os documentos do RAG continuam indexados
 * pelo provedor de embeddings configurado, e a Maritaca pode responder às perguntas.
 * <p>
 * Só é criado com {@code ia-service.maritaca.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "ia-service.maritaca", name = "enabled", havingValue = "true")
public class MaritacaProvider implements ModelProvider {

	private static final Logger log = LoggerFactory.getLogger(MaritacaProvider.class);

	private static final String NAME = "maritaca";

	private static final String COMPLETIONS = "/chat/completions";

	private static final String STREAM_END = "[DONE]";

	private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE = new ParameterizedTypeReference<>() {
	};

	private final RestClient restClient;

	private final WebClient webClient;

	private final ObjectMapper objectMapper;

	private final MaritacaProperties properties;

	private final ModelFallbackExecutor executor;

	public MaritacaProvider(MaritacaProperties properties, ObjectMapper objectMapper,
			CircuitBreakerRegistry circuitBreakers, RetryRegistry retries) {
		if (!StringUtils.hasText(properties.apiKey())) {
			throw new IllegalStateException(
					"A Maritaca está habilitada, mas a chave não foi informada (MARITACA_API_KEY)");
		}
		this.properties = properties;
		this.objectMapper = objectMapper;

		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		JdkClientHttpConnector connector = new JdkClientHttpConnector(httpClient);
		connector.setReadTimeout(properties.readTimeout());

		String authorization = "Bearer " + properties.apiKey();
		this.restClient = RestClient.builder()
				.requestFactory(requestFactory)
				.baseUrl(properties.baseUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, authorization)
				.build();
		this.webClient = WebClient.builder()
				.clientConnector(connector)
				.baseUrl(properties.baseUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, authorization)
				.build();
		this.executor = new ModelFallbackExecutor(NAME, properties.chatModel(), properties.fallbackChatModel(),
				MaritacaErrors::isTransient, circuitBreakers, retries);
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public ChatResult chat(ChatCommand command) {
		var outcome = executor.execute(model -> complete(request(command, model, false, null)));
		return outcome.fallback() ? outcome.value().asFallback() : outcome.value();
	}

	@Override
	public ChatResult structured(ChatCommand command, String jsonSchema) {
		// Mesmo formato da OpenAI: o schema vai em response_format e a resposta vem em JSON
		Map<String, Object> format = Map.of("type", "json_schema",
				"json_schema", Map.of("name", "resposta", "schema", objectMapper.readTree(jsonSchema)));
		var outcome = executor.execute(model -> complete(request(command, model, false, format)));
		return outcome.fallback() ? outcome.value().asFallback() : outcome.value();
	}

	@Override
	public Flux<StreamChunk> chatStream(ChatCommand command) {
		return executor.stream(model -> webClient.post()
						.uri(COMPLETIONS)
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.TEXT_EVENT_STREAM)
						.bodyValue(objectMapper.writeValueAsString(request(command, model, true, null)))
						.retrieve()
						.bodyToFlux(SSE)
						.map(ServerSentEvent::data)
						.takeWhile(data -> !STREAM_END.equals(data.strip()))
						.filter(StringUtils::hasText)
						.map(this::toChunk))
				.onErrorMap(ex -> !(ex instanceof AiProviderException), ex -> {
					log.error("Falha no streaming da Maritaca", ex);
					return executor.toProviderException(ex);
				});
	}

	@Override
	public EmbeddingResult embed(List<String> texts, EmbeddingPurpose purpose) {
		throw new UnsupportedCapabilityException(
				"A Maritaca não oferece embeddings. Use um provedor de embeddings, como gemini ou ollama.");
	}

	@Override
	public ProviderInfo info() {
		return new ProviderInfo(properties.chatModel(), executor.fallbackModel(), null);
	}

	/** Pelos circuit breakers: não faz chamada à API, para não gastar cota em health checks. */
	@Override
	public ProviderHealth health() {
		return executor.health();
	}

	private Map<String, Object> request(ChatCommand command, String model, boolean stream,
			Map<String, Object> responseFormat) {
		List<Map<String, String>> messages = new ArrayList<>();
		if (StringUtils.hasText(command.systemPrompt())) {
			messages.add(Map.of("role", "system", "content", command.systemPrompt()));
		}
		command.history().forEach(message -> messages.add(Map.of(
				"role", switch (message.role()) {
					case USER -> "user";
					case ASSISTANT -> "assistant";
				},
				"content", message.content())));
		messages.add(Map.of("role", "user", "content", command.message()));

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("messages", messages);
		body.put("stream", stream);
		if (stream) {
			// Pede o total de tokens no último evento, para registrar o uso e estimar o custo
			body.put("stream_options", Map.of("include_usage", true));
		}
		if (responseFormat != null) {
			body.put("response_format", responseFormat);
		}
		return body;
	}

	private ChatResult complete(Map<String, Object> body) {
		String json = restClient.post()
				.uri(COMPLETIONS)
				.contentType(MediaType.APPLICATION_JSON)
				.body(objectMapper.writeValueAsString(body))
				.retrieve()
				.body(String.class);
		Completion completion = objectMapper.readValue(json, Completion.class);
		if (completion.choices() == null || completion.choices().isEmpty()
				|| completion.choices().getFirst().message() == null) {
			throw new AiProviderException(NAME, "Resposta vazia do provedor " + NAME, null);
		}
		return new ChatResult(completion.choices().getFirst().message().content(), completion.model(), NAME,
				toUsage(completion.usage()));
	}

	private StreamChunk toChunk(String data) {
		Completion event = objectMapper.readValue(data, Completion.class);
		String text = event.choices() == null || event.choices().isEmpty() || event.choices().getFirst().delta() == null
				? null
				: event.choices().getFirst().delta().content();
		return new StreamChunk(text == null ? "" : text, event.model(), toUsage(event.usage()));
	}

	private static TokenUsage toUsage(Usage usage) {
		return usage == null ? null : new TokenUsage(usage.promptTokens(), usage.completionTokens());
	}

	// Formato da resposta da API (igual ao da OpenAI); só os campos usados aqui

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Completion(String model, List<Choice> choices, Usage usage) {
	}

	/** {@code message} nas respostas completas; {@code delta} em cada evento do streaming. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Choice(Message message, Message delta) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Message(String content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Usage(@JsonProperty("prompt_tokens") Integer promptTokens,
			@JsonProperty("completion_tokens") Integer completionTokens) {
	}

}

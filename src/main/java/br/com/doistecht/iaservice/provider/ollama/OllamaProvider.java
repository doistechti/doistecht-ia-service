package br.com.doistecht.iaservice.provider.ollama;

import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatMessage;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.EmbeddingPurpose;
import br.com.doistecht.iaservice.provider.EmbeddingResult;
import br.com.doistecht.iaservice.provider.ModelFallbackExecutor;
import br.com.doistecht.iaservice.provider.ModelProvider;
import br.com.doistecht.iaservice.provider.ProviderHealth;
import br.com.doistecht.iaservice.provider.ProviderInfo;
import br.com.doistecht.iaservice.provider.StreamChunk;
import br.com.doistecht.iaservice.provider.TokenUsage;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * Provedor Ollama: modelos rodando localmente, sem custo e sem depender da internet.
 * Serve como alternativa ao Gemini e como provedor reserva quando ele está fora do ar.
 * <p>
 * Só é criado com {@code ia-service.ollama.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "ia-service.ollama", name = "enabled", havingValue = "true")
public class OllamaProvider implements ModelProvider {

	private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);

	private static final String NAME = "ollama";

	private final ChatClient chatClient;

	private final OllamaEmbeddingModel embeddingModel;

	private final OllamaApi ollamaApi;

	private final OllamaProperties properties;

	private final ModelFallbackExecutor executor;

	private final ModelFallbackExecutor embeddingExecutor;

	public OllamaProvider(OllamaChatModel chatModel, OllamaEmbeddingModel embeddingModel, OllamaApi ollamaApi,
			OllamaProperties properties, CircuitBreakerRegistry circuitBreakers, RetryRegistry retries) {
		this.chatClient = ChatClient.create(chatModel);
		this.embeddingModel = embeddingModel;
		this.ollamaApi = ollamaApi;
		this.properties = properties;
		this.executor = new ModelFallbackExecutor(NAME, properties.chatModel(), null, OllamaErrors::isTransient,
				circuitBreakers, retries);
		this.embeddingExecutor = new ModelFallbackExecutor(NAME, properties.embeddingModel(), null,
				OllamaErrors::isTransient, circuitBreakers, retries);
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public ChatResult chat(ChatCommand command) {
		return executor.execute(model -> call(prompt(command).options(OllamaChatOptions.builder().model(model))))
				.value();
	}

	@Override
	public ChatResult structured(ChatCommand command, String jsonSchema) {
		// outputSchema envia o JSON Schema no campo "format" da API do Ollama
		return executor.execute(model -> call(prompt(command)
				.options(OllamaChatOptions.builder().model(model).outputSchema(jsonSchema)))).value();
	}

	@Override
	public Flux<StreamChunk> chatStream(ChatCommand command) {
		return executor.stream(model -> prompt(command)
						.options(OllamaChatOptions.builder().model(model))
						.stream()
						.chatResponse())
				.map(OllamaProvider::toChunk)
				.onErrorMap(ex -> !(ex instanceof AiProviderException), ex -> {
					log.error("Falha no streaming do Ollama", ex);
					return executor.toProviderException(ex);
				});
	}

	@Override
	public EmbeddingResult embed(List<String> texts, EmbeddingPurpose purpose) {
		List<String> inputs = texts.stream().map(text -> withTaskPrefix(text, purpose)).toList();
		return embeddingExecutor.execute(model -> {
			EmbeddingResponse response = embeddingModel.call(
					new EmbeddingRequest(inputs, OllamaEmbeddingOptions.builder().model(model).build()));
			List<float[]> vectors = response.getResults().stream().map(result -> normalize(result.getOutput())).toList();
			if (vectors.size() != texts.size()) {
				throw new AiProviderException(NAME, "O Ollama devolveu %d embeddings para %d textos"
						.formatted(vectors.size(), texts.size()), null);
			}
			return new EmbeddingResult(vectors, model, null);
		}).value();
	}

	@Override
	public ProviderInfo info() {
		return new ProviderInfo(properties.chatModel(), null, properties.embeddingModel());
	}

	/** Consulta os modelos baixados no Ollama: confirma que o servidor responde e que os modelos existem. */
	@Override
	public ProviderHealth health() {
		try {
			var response = ollamaApi.listModels();
			Set<String> installed = response == null || response.models() == null
					? Set.of()
					: response.models().stream().map(OllamaApi.Model::name).collect(Collectors.toSet());
			List<String> missing = List.of(properties.chatModel(), properties.embeddingModel()).stream()
					.filter(model -> !installed.contains(model) && !installed.contains(model + ":latest"))
					.toList();
			if (!missing.isEmpty()) {
				return ProviderHealth.down("Modelos não baixados no Ollama: " + String.join(", ", missing));
			}
			return executor.health();
		}
		catch (RuntimeException ex) {
			return ProviderHealth.down("Ollama inacessível: " + ex.getMessage());
		}
	}

	private ChatClient.ChatClientRequestSpec prompt(ChatCommand command) {
		ChatClient.ChatClientRequestSpec request = chatClient.prompt();
		if (StringUtils.hasText(command.systemPrompt())) {
			request = request.system(command.systemPrompt());
		}
		if (!command.history().isEmpty()) {
			request = request.messages(toMessages(command.history()));
		}
		return request.user(command.message());
	}

	private ChatResult call(ChatClient.ChatClientRequestSpec request) {
		ChatResponse response = request.call().chatResponse();
		if (response == null || response.getResult() == null) {
			throw new AiProviderException(NAME, "Resposta vazia do provedor " + NAME, null);
		}
		return new ChatResult(response.getResult().getOutput().getText(), response.getMetadata().getModel(), NAME,
				toUsage(response.getMetadata().getUsage()));
	}

	// O nomic-embed-text foi treinado com prefixos que indicam o uso do texto, o equivalente
	// ao "task type" do Gemini: melhoram a busca quando documentos e perguntas os usam
	private String withTaskPrefix(String text, EmbeddingPurpose purpose) {
		if (!properties.embeddingModel().startsWith("nomic-embed-text")) {
			return text;
		}
		return (purpose == EmbeddingPurpose.QUERY ? "search_query: " : "search_document: ") + text;
	}

	private static StreamChunk toChunk(ChatResponse response) {
		String text = response.getResult() == null ? "" : response.getResult().getOutput().getText();
		return new StreamChunk(text == null ? "" : text, response.getMetadata().getModel(),
				toUsage(response.getMetadata().getUsage()));
	}

	private static float[] normalize(float[] vector) {
		double norm = 0;
		for (float value : vector) {
			norm += value * value;
		}
		norm = Math.sqrt(norm);
		float[] normalized = new float[vector.length];
		for (int i = 0; i < vector.length; i++) {
			normalized[i] = norm == 0 ? 0 : (float) (vector[i] / norm);
		}
		return normalized;
	}

	private static TokenUsage toUsage(Usage usage) {
		if (usage == null || (usage.getPromptTokens() == null && usage.getCompletionTokens() == null)) {
			return null;
		}
		return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens());
	}

	private static List<Message> toMessages(List<ChatMessage> history) {
		return history.stream()
				.<Message>map(m -> switch (m.role()) {
					case USER -> new UserMessage(m.content());
					case ASSISTANT -> new AssistantMessage(m.content());
				})
				.toList();
	}

}

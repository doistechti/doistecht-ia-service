package br.com.doistecht.iaservice.provider.gemini;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.provider.ModelProvider;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatMessage;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.EmbeddingPurpose;
import br.com.doistecht.iaservice.provider.EmbeddingResult;
import br.com.doistecht.iaservice.provider.ModelFallbackExecutor;
import br.com.doistecht.iaservice.provider.ProviderHealth;
import br.com.doistecht.iaservice.provider.ProviderInfo;
import br.com.doistecht.iaservice.provider.StreamChunk;
import br.com.doistecht.iaservice.provider.TokenUsage;
import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * Provedor Gemini (Google AI Studio) implementado com o Spring AI.
 * <p>
 * Chamadas síncronas passam pelo {@link ModelFallbackExecutor} (retry, circuit breaker e
 * modelo reserva). No streaming, o modelo reserva só entra se a falha acontecer antes do
 * primeiro trecho: depois disso, não há como recomeçar a resposta de forma transparente.
 * <p>
 * Embeddings usam o SDK do Google diretamente, e não o Spring AI: a versão 2.0.1 do Spring AI
 * aceita a opção {@code task-type} mas não a envia ao Gemini, e o tipo de tarefa
 * ({@code RETRIEVAL_DOCUMENT} / {@code RETRIEVAL_QUERY}) melhora a qualidade da busca.
 */
@Component
public class GeminiProvider implements ModelProvider {

	private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

	private static final String NAME = "gemini";

	private final ChatClient chatClient;

	private final ModelFallbackExecutor executor;

	private final Client genAiClient;

	/** Sem modelo reserva: embeddings de modelos diferentes não são comparáveis entre si. */
	private final ModelFallbackExecutor embeddingExecutor;

	private final int embeddingDimensions;

	private final String embeddingModel;

	public GeminiProvider(GoogleGenAiChatModel chatModel, Client genAiClient, GeminiProperties properties,
			IaServiceProperties serviceProperties,
			@Value("${spring.ai.google.genai.chat.model}") String primaryModel,
			CircuitBreakerRegistry circuitBreakers, RetryRegistry retries) {
		this.chatClient = ChatClient.create(chatModel);
		this.genAiClient = genAiClient;
		this.executor = new ModelFallbackExecutor(NAME, primaryModel, properties.fallbackModel(),
				GeminiErrors::isTransient, circuitBreakers, retries);
		this.embeddingExecutor = new ModelFallbackExecutor(NAME, properties.embeddingModel(), null,
				GeminiErrors::isTransient, circuitBreakers, retries);
		this.embeddingDimensions = serviceProperties.rag().embeddingDimensions();
		this.embeddingModel = properties.embeddingModel();
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public ChatResult chat(ChatCommand command) {
		return execute(model -> call(prompt(command).options(GoogleGenAiChatOptions.builder().model(model))));
	}

	@Override
	public ChatResult structured(ChatCommand command, String jsonSchema) {
		// outputSchema envia o JSON Schema ao Gemini (responseJsonSchema) e define application/json
		return execute(model -> call(prompt(command)
				.options(GoogleGenAiChatOptions.builder().model(model).outputSchema(jsonSchema))));
	}

	@Override
	public Flux<StreamChunk> chatStream(ChatCommand command) {
		return executor.stream(model -> prompt(command)
						.options(GoogleGenAiChatOptions.builder().model(model))
						.stream()
						.chatResponse())
				.map(GeminiProvider::toChunk)
				.onErrorMap(ex -> !(ex instanceof AiProviderException), ex -> {
					log.error("Falha no streaming do Gemini", ex);
					return executor.toProviderException(ex);
				});
	}

	@Override
	public EmbeddingResult embed(List<String> texts, EmbeddingPurpose purpose) {
		EmbedContentConfig config = EmbedContentConfig.builder()
				.taskType(purpose == EmbeddingPurpose.QUERY ? "RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT")
				.outputDimensionality(embeddingDimensions)
				.build();
		return embeddingExecutor.execute(model -> {
			EmbedContentResponse response = genAiClient.models.embedContent(model, texts, config);
			List<float[]> vectors = response.embeddings().orElse(List.of()).stream()
					.map(GeminiProvider::toNormalizedVector)
					.toList();
			if (vectors.size() != texts.size()) {
				throw new AiProviderException(NAME, "O Gemini devolveu %d embeddings para %d textos"
						.formatted(vectors.size(), texts.size()), null);
			}
			return new EmbeddingResult(vectors, model, null);
		}).value();
	}

	@Override
	public ProviderInfo info() {
		return new ProviderInfo(executor.primaryModel(), executor.fallbackModel(), embeddingModel);
	}

	// Sem chamada de teste: consultar a API gastaria cota do plano gratuito a cada health check
	@Override
	public ProviderHealth health() {
		ProviderHealth chat = executor.health();
		ProviderHealth embeddings = embeddingExecutor.health();
		if (chat.status() == ProviderHealth.Status.UP && !embeddings.isAvailable()) {
			return ProviderHealth.degraded("Chat disponível; embeddings indisponíveis (" + embeddings.detail() + ")");
		}
		return chat;
	}

	private ChatResult execute(Function<String, ChatResult> call) {
		var outcome = executor.execute(call);
		return outcome.fallback() ? outcome.value().asFallback() : outcome.value();
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

	// Erros do provedor sobem sem tratamento: o ModelFallbackExecutor decide se retenta
	private ChatResult call(ChatClient.ChatClientRequestSpec request) {
		ChatResponse response = request.call().chatResponse();
		if (response == null || response.getResult() == null) {
			throw new AiProviderException(NAME, "Resposta vazia do provedor " + NAME, null);
		}
		return new ChatResult(
				response.getResult().getOutput().getText(),
				response.getMetadata().getModel(),
				NAME,
				toUsage(response.getMetadata().getUsage()));
	}

	private static StreamChunk toChunk(ChatResponse response) {
		String text = response.getResult() == null ? "" : response.getResult().getOutput().getText();
		return new StreamChunk(text == null ? "" : text, response.getMetadata().getModel(),
				toUsage(response.getMetadata().getUsage()));
	}

	// Abaixo de 3072 dimensões o Gemini não entrega vetores normalizados; normalizar deixa
	// a similaridade por produto escalar equivalente à de cosseno para quem usa a API
	private static float[] toNormalizedVector(ContentEmbedding embedding) {
		List<Float> values = embedding.values().orElse(List.of());
		double norm = 0;
		for (Float value : values) {
			norm += value * value;
		}
		norm = Math.sqrt(norm);
		float[] vector = new float[values.size()];
		for (int i = 0; i < vector.length; i++) {
			vector[i] = norm == 0 ? 0 : (float) (values.get(i) / norm);
		}
		return vector;
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

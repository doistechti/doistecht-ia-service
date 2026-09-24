package br.com.doistecht.iaservice.provider.gemini;

import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatMessage;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.ModelFallbackExecutor;
import br.com.doistecht.iaservice.provider.StreamChunk;
import br.com.doistecht.iaservice.provider.TokenUsage;
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
 * modelo reserva). O streaming usa só o modelo principal, sem novas tentativas: depois que
 * o primeiro trecho chega ao cliente, não há como recomeçar a resposta de forma transparente.
 */
@Component
public class GeminiProvider implements AiProvider {

	private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

	private static final String NAME = "gemini";

	private final ChatClient chatClient;

	private final ModelFallbackExecutor executor;

	public GeminiProvider(GoogleGenAiChatModel chatModel, GeminiProperties properties,
			@Value("${spring.ai.google.genai.chat.model}") String primaryModel,
			CircuitBreakerRegistry circuitBreakers, RetryRegistry retries) {
		this.chatClient = ChatClient.create(chatModel);
		this.executor = new ModelFallbackExecutor(NAME, primaryModel, properties.fallbackModel(),
				GeminiErrors::isTransient, circuitBreakers, retries);
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
		return prompt(command)
				.options(GoogleGenAiChatOptions.builder().model(executor.primaryModel()))
				.stream()
				.chatResponse()
				.map(GeminiProvider::toChunk)
				.onErrorMap(ex -> !(ex instanceof AiProviderException), ex -> {
					log.error("Falha no streaming do Gemini", ex);
					return executor.toProviderException(ex);
				});
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

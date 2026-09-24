package br.com.doistecht.iaservice.provider.gemini;

import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatMessage;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.StreamChunk;
import br.com.doistecht.iaservice.provider.TokenUsage;
import java.util.List;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * Provedor Gemini (Google AI Studio) implementado com o Spring AI.
 */
@Component
public class GeminiProvider implements AiProvider {

	private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

	private static final String NAME = "gemini";

	private final ChatClient chatClient;

	public GeminiProvider(GoogleGenAiChatModel chatModel) {
		this.chatClient = ChatClient.create(chatModel);
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public ChatResult chat(ChatCommand command) {
		return call(prompt(command));
	}

	@Override
	public Flux<StreamChunk> chatStream(ChatCommand command) {
		return prompt(command).stream()
				.chatResponse()
				.map(GeminiProvider::toChunk)
				.onErrorMap(ex -> !(ex instanceof AiProviderException), ex -> {
					log.error("Falha no streaming do Gemini", ex);
					return new AiProviderException(NAME, "Falha no streaming do provedor " + NAME, ex);
				});
	}

	@Override
	public ChatResult structured(ChatCommand command, String jsonSchema) {
		// outputSchema envia o JSON Schema ao Gemini (responseJsonSchema) e define application/json
		return call(prompt(command).options(GoogleGenAiChatOptions.builder().outputSchema(jsonSchema)));
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
		ChatResponse response;
		try {
			response = request.call().chatResponse();
		}
		catch (RuntimeException ex) {
			log.error("Falha ao chamar o Gemini", ex);
			throw new AiProviderException(NAME, "Falha ao chamar o provedor " + NAME, ex);
		}

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

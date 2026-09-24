package br.com.doistecht.iaservice.provider.gemini;

import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
		ChatClient.ChatClientRequestSpec request = chatClient.prompt().user(command.message());
		if (StringUtils.hasText(command.systemPrompt())) {
			request = request.system(command.systemPrompt());
		}

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
				NAME);
	}

}

package br.com.doistecht.iaservice.provider;

import java.util.List;

/**
 * Pedido de chat independente de provedor.
 *
 * @param systemPrompt instrução de comportamento do modelo (opcional)
 * @param history      mensagens anteriores da conversa, da mais antiga para a mais recente
 * @param message      mensagem atual do usuário
 * @param provider     provedor pedido pelo cliente (ex.: {@code ollama}); {@code null} deixa o gateway escolher
 */
public record ChatCommand(String systemPrompt, List<ChatMessage> history, String message, String provider) {

	public ChatCommand {
		history = history == null ? List.of() : List.copyOf(history);
	}

	public ChatCommand(String systemPrompt, List<ChatMessage> history, String message) {
		this(systemPrompt, history, message, null);
	}

	public ChatCommand(String systemPrompt, String message) {
		this(systemPrompt, List.of(), message, null);
	}

	public ChatCommand withProvider(String provider) {
		return new ChatCommand(systemPrompt, history, message, provider);
	}

}

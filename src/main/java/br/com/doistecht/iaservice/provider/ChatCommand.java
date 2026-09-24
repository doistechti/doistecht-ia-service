package br.com.doistecht.iaservice.provider;

import java.util.List;

/**
 * Pedido de chat independente de provedor.
 *
 * @param systemPrompt instrução de comportamento do modelo (opcional)
 * @param history      mensagens anteriores da conversa, da mais antiga para a mais recente
 * @param message      mensagem atual do usuário
 */
public record ChatCommand(String systemPrompt, List<ChatMessage> history, String message) {

	public ChatCommand {
		history = history == null ? List.of() : List.copyOf(history);
	}

	public ChatCommand(String systemPrompt, String message) {
		this(systemPrompt, List.of(), message);
	}

}

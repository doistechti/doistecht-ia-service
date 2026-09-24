package br.com.doistecht.iaservice.provider;

/**
 * Mensagem anterior de uma conversa, enviada como histórico.
 *
 * @param role    autor da mensagem
 * @param content texto da mensagem
 */
public record ChatMessage(Role role, String content) {

	public enum Role {
		USER, ASSISTANT
	}

}

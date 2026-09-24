package br.com.doistecht.iaservice.provider;

/**
 * Pedido de chat independente de provedor.
 *
 * @param systemPrompt instrução de comportamento do modelo (opcional)
 * @param message      mensagem do usuário
 */
public record ChatCommand(String systemPrompt, String message) {
}

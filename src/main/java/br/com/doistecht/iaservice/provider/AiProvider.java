package br.com.doistecht.iaservice.provider;

/**
 * Contrato comum a todos os provedores de IA.
 * <p>
 * Nenhuma classe fora do pacote {@code provider} deve depender do Spring AI
 * ou de um provedor específico: elas conversam apenas com esta interface.
 */
public interface AiProvider {

	/** Identificador do provedor, ex.: {@code gemini}. */
	String name();

	ChatResult chat(ChatCommand command);

}

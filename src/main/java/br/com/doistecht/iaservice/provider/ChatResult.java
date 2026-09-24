package br.com.doistecht.iaservice.provider;

/**
 * Resposta de chat independente de provedor.
 *
 * @param content  texto gerado pelo modelo
 * @param model    modelo que gerou a resposta
 * @param provider provedor utilizado
 * @param usage    tokens consumidos (pode ser nulo)
 */
public record ChatResult(String content, String model, String provider, TokenUsage usage) {

	public ChatResult(String content, String model, String provider) {
		this(content, model, provider, null);
	}

}

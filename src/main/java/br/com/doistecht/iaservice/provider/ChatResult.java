package br.com.doistecht.iaservice.provider;

/**
 * Resposta de chat independente de provedor.
 *
 * @param content  texto gerado pelo modelo
 * @param model    modelo que gerou a resposta
 * @param provider provedor utilizado
 * @param usage    tokens consumidos (pode ser nulo)
 * @param fallback {@code true} quando o modelo principal falhou e a resposta veio do modelo reserva
 */
public record ChatResult(String content, String model, String provider, TokenUsage usage, boolean fallback) {

	public ChatResult(String content, String model, String provider, TokenUsage usage) {
		this(content, model, provider, usage, false);
	}

	public ChatResult(String content, String model, String provider) {
		this(content, model, provider, null, false);
	}

	public ChatResult asFallback() {
		return new ChatResult(content, model, provider, usage, true);
	}

}

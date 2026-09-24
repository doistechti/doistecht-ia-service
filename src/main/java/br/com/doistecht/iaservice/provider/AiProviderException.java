package br.com.doistecht.iaservice.provider;

/**
 * Falha ao se comunicar com um provedor de IA.
 */
public class AiProviderException extends RuntimeException {

	private final String provider;

	public AiProviderException(String provider, String message, Throwable cause) {
		super(message, cause);
		this.provider = provider;
	}

	public String getProvider() {
		return provider;
	}

}

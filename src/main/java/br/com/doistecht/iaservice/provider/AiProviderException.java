package br.com.doistecht.iaservice.provider;

/**
 * Falha ao se comunicar com um provedor de IA.
 */
public class AiProviderException extends RuntimeException {

	public enum Reason {
		/** Provedor fora do ar ou sobrecarregado; vale tentar de novo mais tarde (HTTP 503). */
		UNAVAILABLE,
		/** Falha que não se resolve com nova tentativa, ex.: requisição recusada (HTTP 502). */
		FAILED
	}

	private final String provider;

	private final Reason reason;

	private final Long retryAfterSeconds;

	public AiProviderException(String provider, Reason reason, String message, Long retryAfterSeconds,
			Throwable cause) {
		super(message, cause);
		this.provider = provider;
		this.reason = reason;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public AiProviderException(String provider, String message, Throwable cause) {
		this(provider, Reason.FAILED, message, null, cause);
	}

	public String getProvider() {
		return provider;
	}

	public Reason getReason() {
		return reason;
	}

	/** Sugestão de espera antes de tentar de novo, quando conhecida (ex.: circuit breaker aberto). */
	public Long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}

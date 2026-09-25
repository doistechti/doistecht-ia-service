package br.com.doistecht.iaservice.provider;

/**
 * Situação de um provedor.
 *
 * @param detail explicação legível (ex.: qual circuit breaker está aberto)
 */
public record ProviderHealth(Status status, String detail) {

	public enum Status {
		/** Respondendo normalmente. */
		UP,
		/** Parte dos modelos indisponível (ex.: circuito do modelo principal aberto). */
		DEGRADED,
		/** Fora do ar. */
		DOWN
	}

	public static ProviderHealth up(String detail) {
		return new ProviderHealth(Status.UP, detail);
	}

	public static ProviderHealth degraded(String detail) {
		return new ProviderHealth(Status.DEGRADED, detail);
	}

	public static ProviderHealth down(String detail) {
		return new ProviderHealth(Status.DOWN, detail);
	}

	public boolean isAvailable() {
		return status != Status.DOWN;
	}

}

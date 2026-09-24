package br.com.doistecht.iaservice.ratelimit;

/**
 * Situação dos limites do cliente após consumir uma requisição.
 * Valores negativos indicam que os limites não puderam ser consultados.
 */
public record RateLimitDecision(long minuteLimit, long minuteRemaining, long dailyQuota, long dailyRemaining) {

	public static final RateLimitDecision UNAVAILABLE = new RateLimitDecision(-1, -1, -1, -1);

	public boolean isAvailable() {
		return minuteLimit >= 0;
	}

}

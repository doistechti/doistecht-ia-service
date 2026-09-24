package br.com.doistecht.iaservice.ratelimit;

public class RateLimitExceededException extends RuntimeException {

	public enum Limit {
		PER_MINUTE("Limite de requisições por minuto atingido."),
		DAILY_QUOTA("Cota diária de requisições atingida.");

		private final String message;

		Limit(String message) {
			this.message = message;
		}

	}

	private final Limit limit;

	private final long retryAfterSeconds;

	public RateLimitExceededException(Limit limit, long retryAfterSeconds) {
		super(limit.message);
		this.limit = limit;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public Limit getLimit() {
		return limit;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}

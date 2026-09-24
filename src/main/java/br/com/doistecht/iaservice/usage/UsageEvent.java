package br.com.doistecht.iaservice.usage;

import br.com.doistecht.iaservice.provider.TokenUsage;
import java.time.Instant;

/**
 * Dados de uma chamada ao modelo, a serem gravados como {@link UsageRecord}.
 */
public record UsageEvent(
		Long clientId,
		String endpoint,
		Operation operation,
		String provider,
		String model,
		TokenUsage usage,
		long latencyMs,
		boolean cacheHit,
		boolean success,
		Instant occurredAt) {

	public enum Operation {
		CHAT("chat"), STREAM("stream"), STRUCTURED("structured");

		private final String value;

		Operation(String value) {
			this.value = value;
		}

		public String value() {
			return value;
		}
	}

}

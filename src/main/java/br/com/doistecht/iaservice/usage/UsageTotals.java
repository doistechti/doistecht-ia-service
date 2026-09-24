package br.com.doistecht.iaservice.usage;

import java.math.BigDecimal;

/**
 * Totais de uso em um período. Somas sem registros chegam do banco como nulas
 * e são convertidas para zero.
 */
public record UsageTotals(
		Long calls,
		Long successfulCalls,
		Long cacheHits,
		Long promptTokens,
		Long outputTokens,
		BigDecimal estimatedCost) {

	public UsageTotals {
		calls = zeroIfNull(calls);
		successfulCalls = zeroIfNull(successfulCalls);
		cacheHits = zeroIfNull(cacheHits);
		promptTokens = zeroIfNull(promptTokens);
		outputTokens = zeroIfNull(outputTokens);
		estimatedCost = estimatedCost == null ? BigDecimal.ZERO : estimatedCost;
	}

	private static Long zeroIfNull(Long value) {
		return value == null ? 0L : value;
	}

}

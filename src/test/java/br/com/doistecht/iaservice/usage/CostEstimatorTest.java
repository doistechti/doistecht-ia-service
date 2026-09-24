package br.com.doistecht.iaservice.usage;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.config.IaServiceProperties.ModelPrice;
import br.com.doistecht.iaservice.provider.TokenUsage;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CostEstimatorTest {

	private final CostEstimator estimator = new CostEstimator(new IaServiceProperties("admin",
			new IaServiceProperties.Auth(Duration.ofSeconds(30)), new IaServiceProperties.Cache(true, Duration.ofHours(1)),
			Map.of(
					"gemini-2.5-flash", new ModelPrice(new BigDecimal("0.30"), new BigDecimal("2.50")),
					"gemini-2.5-flash-lite", new ModelPrice(new BigDecimal("0.10"), new BigDecimal("0.40")))));

	@Test
	void shouldEstimateCostPerMillionTokens() {
		// 1.000 tokens de entrada a 0,30/M + 2.000 de saída a 2,50/M = 0,0003 + 0,005
		assertThat(estimator.estimate("gemini-2.5-flash", new TokenUsage(1_000, 2_000)))
				.isEqualByComparingTo("0.0053");
	}

	@Test
	void shouldUseMostSpecificModelName() {
		assertThat(estimator.estimate("models/gemini-2.5-flash-lite-001", new TokenUsage(1_000_000, 0)))
				.isEqualByComparingTo("0.10");
	}

	@Test
	void shouldReturnNullForUnknownModelOrMissingUsage() {
		assertThat(estimator.estimate("outro-modelo", new TokenUsage(10, 10))).isNull();
		assertThat(estimator.estimate("gemini-2.5-flash", null)).isNull();
	}

}

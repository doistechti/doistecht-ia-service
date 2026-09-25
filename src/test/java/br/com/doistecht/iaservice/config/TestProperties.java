package br.com.doistecht.iaservice.config;

import br.com.doistecht.iaservice.config.IaServiceProperties.ModelPrice;
import java.time.Duration;
import java.util.Map;
import org.springframework.util.unit.DataSize;

/** Configurações com os valores padrão, para testes unitários que não sobem o Spring. */
public final class TestProperties {

	private TestProperties() {
	}

	public static IaServiceProperties defaults() {
		return withPricing(Map.of());
	}

	public static IaServiceProperties withPricing(Map<String, ModelPrice> pricing) {
		return new IaServiceProperties("admin", new IaServiceProperties.Auth(Duration.ofSeconds(30)),
				new IaServiceProperties.Cache(true, Duration.ofHours(1)), pricing, rag(1000, 200),
				new IaServiceProperties.Providers("gemini", null));
	}

	public static IaServiceProperties.Rag rag(int chunkSize, int chunkOverlap) {
		return new IaServiceProperties.Rag(chunkSize, chunkOverlap, DataSize.ofMegabytes(10), 500, 50, Duration.ZERO, 4,
				20, 0.5, 768, "gemini");
	}

}

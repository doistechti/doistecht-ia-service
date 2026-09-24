package br.com.doistecht.iaservice.usage;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.config.IaServiceProperties.ModelPrice;
import br.com.doistecht.iaservice.provider.TokenUsage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Estima o custo de uma chamada com base na tabela {@code ia-service.pricing}.
 * <p>
 * No plano gratuito do Gemini o custo real é zero; o valor serve para métricas e
 * para saber quanto o uso custaria em um plano pago.
 */
@Component
public class CostEstimator {

	private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

	private final Map<String, ModelPrice> pricing;

	public CostEstimator(IaServiceProperties properties) {
		this.pricing = properties.pricing();
	}

	/** Custo estimado em dólares, ou {@code null} se o modelo não tiver preço configurado. */
	public BigDecimal estimate(String model, TokenUsage usage) {
		if (model == null || usage == null) {
			return null;
		}
		ModelPrice price = priceFor(model);
		if (price == null) {
			return null;
		}
		return cost(usage.promptTokens(), price.inputPerMillion())
				.add(cost(usage.outputTokens(), price.outputPerMillion()))
				.setScale(6, RoundingMode.HALF_UP);
	}

	// O provedor pode devolver variações do nome (ex.: "models/gemini-2.5-flash-001"):
	// usa o nome configurado mais longo contido no nome devolvido
	private ModelPrice priceFor(String model) {
		return pricing.entrySet().stream()
				.filter(entry -> model.contains(entry.getKey()))
				.max(Comparator.comparingInt(entry -> entry.getKey().length()))
				.map(Map.Entry::getValue)
				.orElse(null);
	}

	private static BigDecimal cost(Integer tokens, BigDecimal pricePerMillion) {
		if (tokens == null || pricePerMillion == null) {
			return BigDecimal.ZERO;
		}
		return pricePerMillion.multiply(BigDecimal.valueOf(tokens)).divide(ONE_MILLION, 10, RoundingMode.HALF_UP);
	}

}

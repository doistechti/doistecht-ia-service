package br.com.doistecht.iaservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Métricas próprias do gateway, expostas no Prometheus e usadas no dashboard do Grafana.
 * <p>
 * A tag {@code client} usa o nome do cliente: o número de clientes é pequeno e controlado
 * pelo admin, então não há risco de explodir a cardinalidade das séries.
 */
@Component
public class GatewayMetrics {

	public static final String CALLS = "ia.gateway.calls";

	public static final String TOKENS = "ia.gateway.tokens";

	public static final String LATENCY = "ia.gateway.latency";

	public static final String RATE_LIMIT_REJECTED = "ia.ratelimit.rejected";

	/** Resultado de uma chamada ao modelo. */
	public enum Outcome {
		SUCCESS("success"), FAILURE("failure"), CACHE_HIT("cache_hit");

		private final String tag;

		Outcome(String tag) {
			this.tag = tag;
		}
	}

	private final MeterRegistry registry;

	public GatewayMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	public void recordCall(String client, String operation, String provider, Outcome outcome, boolean fallback,
			Duration latency, Integer promptTokens, Integer outputTokens) {
		String clientTag = client == null ? "desconhecido" : client;
		Counter.builder(CALLS)
				.description("Chamadas aos modelos de IA")
				.tag("client", clientTag)
				.tag("operation", operation)
				.tag("provider", provider)
				.tag("outcome", outcome.tag)
				.tag("fallback", String.valueOf(fallback))
				.register(registry)
				.increment();

		Timer.builder(LATENCY)
				.description("Latência das chamadas aos modelos de IA")
				.tag("operation", operation)
				.tag("provider", provider)
				.tag("outcome", outcome.tag)
				.publishPercentileHistogram()
				.register(registry)
				.record(latency);

		addTokens(clientTag, operation, "input", promptTokens);
		addTokens(clientTag, operation, "output", outputTokens);
	}

	public void recordRateLimitRejected(String client, String limit) {
		Counter.builder(RATE_LIMIT_REJECTED)
				.description("Requisições recusadas por rate limit ou cota diária")
				.tag("client", client)
				.tag("limit", limit)
				.register(registry)
				.increment();
	}

	private void addTokens(String client, String operation, String type, Integer tokens) {
		if (tokens == null || tokens <= 0) {
			return;
		}
		Counter.builder(TOKENS)
				.description("Tokens consumidos nos modelos de IA")
				.tag("client", client)
				.tag("operation", operation)
				.tag("type", type)
				.register(registry)
				.increment(tokens);
	}

}

package br.com.doistecht.iaservice.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.doistecht.iaservice.AbstractMockedProviderIT;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.TokenUsage;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Confere que as métricas do gateway chegam ao formato do Prometheus, que é o que o
 * dashboard do Grafana consulta.
 */
class MetricsIT extends AbstractMockedProviderIT {

	@Autowired
	private PrometheusMeterRegistry prometheus;

	@Test
	void shouldExposeGatewayMetricsForPrometheus() throws Exception {
		TestClient client = createClient(1, 100);
		given(gemini.chat(any(ChatCommand.class)))
				.willReturn(new ChatResult("Olá", "gemini-2.5-flash", "gemini", new TokenUsage(12, 30)));

		chat(client).andExpect(status().isOk());
		chat(client).andExpect(status().isTooManyRequests());

		String clientName = clientService.get(client.id()).getName();
		String scrape = prometheus.scrape();
		assertThat(scrape)
				.contains("ia_gateway_calls_total{application=\"ia-service\",client=\"%s\",fallback=\"false\",operation=\"chat\",outcome=\"success\",provider=\"gemini\"} 1.0"
						.formatted(clientName))
				.contains("ia_gateway_tokens_total{application=\"ia-service\",client=\"%s\",operation=\"chat\",type=\"output\"} 30.0"
						.formatted(clientName))
				.contains("ia_ratelimit_rejected_total{application=\"ia-service\",client=\"%s\",limit=\"PER_MINUTE\"} 1.0"
						.formatted(clientName))
				.contains("ia_gateway_latency_seconds_bucket");
	}

	private ResultActions chat(TestClient client) throws Exception {
		return mockMvc.perform(post("/v1/chat")
				.header(ApiKeyFilter.HEADER, client.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"message": "%s"}
						""".formatted(UUID.randomUUID())));
	}

}

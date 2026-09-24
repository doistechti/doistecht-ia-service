package br.com.doistecht.iaservice.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.doistecht.iaservice.AbstractMockedProviderIT;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.TokenUsage;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Registro de uso e cache de respostas, passando pelo GatewayAiProvider real,
 * com PostgreSQL e Redis reais.
 */
class UsageIT extends AbstractMockedProviderIT {

	@Autowired
	private UsageRecordRepository usageRepository;

	@Test
	void shouldRecordUsageWithTokensAndEstimatedCost() throws Exception {
		TestClient client = createClient();
		given(gemini.chat(any(ChatCommand.class)))
				.willReturn(new ChatResult("Olá!", "gemini-2.5-flash", "gemini", new TokenUsage(1_000, 2_000)));

		chat(client, "Mensagem " + UUID.randomUUID()).andExpect(status().isOk());

		UsageRecord record = awaitRecords(client, 1).getFirst();
		assertThat(record.getEndpoint()).isEqualTo("/v1/chat");
		assertThat(record.getOperation()).isEqualTo("chat");
		assertThat(record.getModel()).isEqualTo("gemini-2.5-flash");
		assertThat(record.getPromptTokens()).isEqualTo(1_000);
		assertThat(record.getOutputTokens()).isEqualTo(2_000);
		assertThat(record.getEstimatedCost()).isEqualByComparingTo("0.0053");
		assertThat(record.isSuccess()).isTrue();
		assertThat(record.isCacheHit()).isFalse();
	}

	@Test
	void shouldServeRepeatedRequestFromCache() throws Exception {
		TestClient client = createClient();
		String message = "Pergunta repetida " + UUID.randomUUID();
		given(gemini.chat(any(ChatCommand.class))).willReturn(new ChatResult("Resposta", "m", "gemini"));

		chat(client, message).andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Resposta"));
		chat(client, message).andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Resposta"));

		verify(gemini, times(1)).chat(any(ChatCommand.class));
		List<UsageRecord> records = awaitRecords(client, 2);
		assertThat(records).extracting(UsageRecord::isCacheHit).containsExactlyInAnyOrder(true, false);
	}

	@Test
	void shouldNotShareCacheBetweenClients() throws Exception {
		TestClient first = createClient();
		TestClient second = createClient();
		String message = "Mesma pergunta " + UUID.randomUUID();
		given(gemini.chat(any(ChatCommand.class))).willReturn(new ChatResult("Resposta", "m", "gemini"));

		chat(first, message).andExpect(status().isOk());
		chat(second, message).andExpect(status().isOk());

		assertThat(awaitRecords(second, 1).getFirst().isCacheHit()).isFalse();
	}

	@Test
	void shouldRecordFailedCalls() throws Exception {
		TestClient client = createClient();
		given(gemini.chat(any(ChatCommand.class))).willThrow(new AiProviderException("gemini", "falhou", null));

		chat(client, "Vai falhar " + UUID.randomUUID()).andExpect(status().isBadGateway());

		assertThat(awaitRecords(client, 1).getFirst().isSuccess()).isFalse();
	}

	@Test
	void shouldReportTotalsForClientAndAdmin() throws Exception {
		TestClient client = createClient();
		given(gemini.chat(any(ChatCommand.class)))
				.willReturn(new ChatResult("Olá!", "gemini-2.5-flash", "gemini", new TokenUsage(10, 20)));

		String message = "Totais " + UUID.randomUUID();
		chat(client, message).andExpect(status().isOk());
		chat(client, message).andExpect(status().isOk());
		awaitRecords(client, 2);

		mockMvc.perform(get("/v1/usage").header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totals.calls").value(2))
				.andExpect(jsonPath("$.totals.successfulCalls").value(2))
				.andExpect(jsonPath("$.totals.cacheHits").value(1))
				.andExpect(jsonPath("$.totals.promptTokens").value(10))
				.andExpect(jsonPath("$.totals.outputTokens").value(20));

		mockMvc.perform(get("/v1/admin/usage").header(ApiKeyFilter.HEADER, ADMIN_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clients[?(@.clientId == %d)].totals.calls".formatted(client.id())).value(2));
		verify(gemini, atLeastOnce()).chat(any(ChatCommand.class));
	}

	@Test
	void shouldRejectInvalidPeriod() throws Exception {
		TestClient client = createClient();

		mockMvc.perform(get("/v1/usage?from=2026-09-10&to=2026-09-01").header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(status().isBadRequest());
	}

	private ResultActions chat(TestClient client, String message) throws Exception {
		return mockMvc.perform(post("/v1/chat")
				.header(ApiKeyFilter.HEADER, client.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"message": "%s"}
						""".formatted(message)));
	}

	// O uso é gravado de forma assíncrona
	private List<UsageRecord> awaitRecords(TestClient client, int expected) {
		return await().atMost(Duration.ofSeconds(10))
				.until(() -> usageRepository.findByClientIdOrderByCreatedAtDesc(client.id()),
						records -> records.size() >= expected);
	}

}

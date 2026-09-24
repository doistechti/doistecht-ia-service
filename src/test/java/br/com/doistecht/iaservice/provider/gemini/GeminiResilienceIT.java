package br.com.doistecht.iaservice.provider.gemini;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.doistecht.iaservice.AbstractIntegrationTest;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Resiliência de ponta a ponta com a API do Gemini simulada pelo WireMock: o SDK do Google
 * faz chamadas HTTP de verdade, passando por retry, circuit breaker, fallback e cache.
 */
class GeminiResilienceIT extends AbstractIntegrationTest {

	private static final String PRIMARY_PATH = ".*/models/gemini-2\\.5-flash:generateContent";

	private static final String FALLBACK_PATH = ".*/models/gemini-2\\.5-flash-lite:generateContent";

	private static final String STREAM_PATH = ".*/models/gemini-2\\.5-flash:streamGenerateContent";

	static final WireMockServer GEMINI = new WireMockServer(options().dynamicPort());

	static {
		GEMINI.start();
	}

	@DynamicPropertySource
	static void geminiProperties(DynamicPropertyRegistry registry) {
		registry.add("ia-service.gemini.base-url", GEMINI::baseUrl);
		registry.add("ia-service.gemini.timeout", () -> "1s");
		registry.add("ia-service.gemini.fallback-model", () -> "gemini-2.5-flash-lite");
		registry.add("spring.ai.google.genai.chat.model", () -> "gemini-2.5-flash");
		registry.add("resilience4j.retry.configs.default.wait-duration", () -> "10ms");
		registry.add("resilience4j.circuitbreaker.configs.default.sliding-window-size", () -> "4");
		registry.add("resilience4j.circuitbreaker.configs.default.minimum-number-of-calls", () -> "4");
		registry.add("resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state", () -> "60s");
		registry.add("resilience4j.circuitbreaker.configs.default.automatic-transition-from-open-to-half-open-enabled",
				() -> "false");
	}

	@Autowired
	private CircuitBreakerRegistry circuitBreakers;

	private TestClient client;

	@BeforeEach
	void setUp() {
		GEMINI.resetAll();
		circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
		client = createClient();
	}

	@Test
	void shouldCallGeminiApiAndReturnResponse() throws Exception {
		GEMINI.stubFor(post(urlPathMatching(PRIMARY_PATH)).willReturn(okJson(response("Olá!", "gemini-2.5-flash"))));

		chat(uniqueMessage()).andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("Olá!"))
				.andExpect(jsonPath("$.model").value("gemini-2.5-flash"))
				.andExpect(jsonPath("$.fallback").value(false));

		GEMINI.verify(1, postRequestedFor(urlPathMatching(PRIMARY_PATH))
				.withHeader("x-goog-api-key", equalTo("test-gemini-key")));
	}

	@Test
	void shouldRetryTransientErrors() throws Exception {
		GEMINI.stubFor(post(urlPathMatching(PRIMARY_PATH)).inScenario("instavel").whenScenarioStateIs(STARTED)
				.willReturn(error(503)).willSetStateTo("falhou-1"));
		GEMINI.stubFor(post(urlPathMatching(PRIMARY_PATH)).inScenario("instavel").whenScenarioStateIs("falhou-1")
				.willReturn(error(429)).willSetStateTo("falhou-2"));
		GEMINI.stubFor(post(urlPathMatching(PRIMARY_PATH)).inScenario("instavel").whenScenarioStateIs("falhou-2")
				.willReturn(okJson(response("Deu certo", "gemini-2.5-flash"))));

		chat(uniqueMessage()).andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("Deu certo"))
				.andExpect(jsonPath("$.fallback").value(false));

		GEMINI.verify(3, postRequestedFor(urlPathMatching(PRIMARY_PATH)));
	}

	@Test
	void shouldFallBackToSecondaryModelWhenPrimaryIsDown() throws Exception {
		GEMINI.stubFor(post(urlPathMatching(PRIMARY_PATH)).willReturn(error(503)));
		GEMINI.stubFor(post(urlPathMatching(FALLBACK_PATH))
				.willReturn(okJson(response("Resposta do reserva", "gemini-2.5-flash-lite"))));

		chat(uniqueMessage()).andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("Resposta do reserva"))
				.andExpect(jsonPath("$.model").value("gemini-2.5-flash-lite"))
				.andExpect(jsonPath("$.fallback").value(true));

		GEMINI.verify(3, postRequestedFor(urlPathMatching(PRIMARY_PATH)));
		GEMINI.verify(1, postRequestedFor(urlPathMatching(FALLBACK_PATH)));
	}

	@Test
	void shouldFallBackWhenPrimaryTimesOut() throws Exception {
		GEMINI.stubFor(post(urlPathMatching(PRIMARY_PATH))
				.willReturn(okJson(response("Atrasado", "gemini-2.5-flash")).withFixedDelay(2_000)));
		GEMINI.stubFor(post(urlPathMatching(FALLBACK_PATH))
				.willReturn(okJson(response("Rápido", "gemini-2.5-flash-lite"))));

		chat(uniqueMessage()).andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("Rápido"))
				.andExpect(jsonPath("$.fallback").value(true));
	}

	@Test
	void shouldNotRetryNorFallBackOnPermanentError() throws Exception {
		GEMINI.stubFor(post(urlPathMatching(PRIMARY_PATH)).willReturn(error(400)));
		GEMINI.stubFor(post(urlPathMatching(FALLBACK_PATH)).willReturn(okJson(response("x", "gemini-2.5-flash-lite"))));

		chat(uniqueMessage()).andExpect(status().isBadGateway());

		GEMINI.verify(1, postRequestedFor(urlPathMatching(PRIMARY_PATH)));
		GEMINI.verify(0, postRequestedFor(urlPathMatching(FALLBACK_PATH)));
	}

	@Test
	void shouldOpenCircuitAndFailFastWhenEverythingIsDown() throws Exception {
		GEMINI.stubFor(post(urlPathMatching(PRIMARY_PATH)).willReturn(error(503)));
		GEMINI.stubFor(post(urlPathMatching(FALLBACK_PATH)).willReturn(error(503)));

		chat(uniqueMessage()).andExpect(status().isServiceUnavailable());
		chat(uniqueMessage()).andExpect(status().isServiceUnavailable());
		int requestsBefore = GEMINI.getAllServeEvents().size();

		// Os dois circuitos estão abertos: a resposta sai na hora, sem chamar o Gemini
		chat(uniqueMessage()).andExpect(status().isServiceUnavailable())
				.andExpect(header().string("Retry-After", "60"))
				.andExpect(jsonPath("$.provider").value("gemini"));
		assertThat(GEMINI.getAllServeEvents()).hasSize(requestsBefore);
	}

	@Test
	void shouldServeRepeatedQuestionFromCache() throws Exception {
		GEMINI.stubFor(post(urlPathMatching(PRIMARY_PATH)).willReturn(okJson(response("Olá!", "gemini-2.5-flash"))));
		String message = uniqueMessage();

		chat(message).andExpect(status().isOk());
		chat(message).andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Olá!"));

		GEMINI.verify(1, postRequestedFor(urlPathMatching(PRIMARY_PATH)));
	}

	@Test
	void shouldNotCacheFallbackResponses() throws Exception {
		GEMINI.stubFor(post(urlPathMatching(PRIMARY_PATH)).willReturn(error(503)));
		GEMINI.stubFor(post(urlPathMatching(FALLBACK_PATH))
				.willReturn(okJson(response("Resposta do reserva", "gemini-2.5-flash-lite"))));
		String message = uniqueMessage();

		chat(message).andExpect(jsonPath("$.fallback").value(true));
		chat(message).andExpect(jsonPath("$.fallback").value(true));

		GEMINI.verify(2, postRequestedFor(urlPathMatching(FALLBACK_PATH)));
	}

	@Test
	void shouldStreamChunksFromGemini() throws Exception {
		String sse = "data: " + response("Olá", "gemini-2.5-flash") + "\n\n"
				+ "data: " + response(", mundo!", "gemini-2.5-flash") + "\n\n";
		GEMINI.stubFor(post(urlPathMatching(STREAM_PATH)).willReturn(aResponse()
				.withHeader("Content-Type", "text/event-stream")
				.withBody(sse)));

		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/v1/chat/stream")
						.header(ApiKeyFilter.HEADER, client.apiKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Oi"}
								"""))
				.andExpect(request().asyncStarted())
				.andReturn();

		String body = mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(body)
				.contains("data:{\"content\":\"Olá\"}")
				.contains("data:{\"content\":\", mundo!\"}")
				.contains("event:done");
	}

	private ResultActions chat(String message) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post("/v1/chat")
				.header(ApiKeyFilter.HEADER, client.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"message": "%s"}
						""".formatted(message)));
	}

	private static String uniqueMessage() {
		return "Pergunta " + UUID.randomUUID();
	}

	/** Resposta no formato da API generateContent do Gemini. */
	private static String response(String text, String model) {
		return """
				{
				  "candidates": [
				    {"content": {"parts": [{"text": "%s"}], "role": "model"}, "finishReason": "STOP", "index": 0}
				  ],
				  "usageMetadata": {"promptTokenCount": 5, "candidatesTokenCount": 3, "totalTokenCount": 8},
				  "modelVersion": "%s"
				}""".formatted(text, model).replace("\n", "");
	}

	private static ResponseDefinitionBuilder error(int status) {
		return aResponse().withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody("""
						{"error": {"code": %d, "message": "erro simulado", "status": "SIMULADO"}}"""
						.formatted(status));
	}

}

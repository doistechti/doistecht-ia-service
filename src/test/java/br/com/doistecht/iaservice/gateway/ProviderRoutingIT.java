package br.com.doistecht.iaservice.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.doistecht.iaservice.AbstractIntegrationTest;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Dois provedores de verdade (clientes HTTP do Gemini e do Ollama), com as duas APIs
 * simuladas pelo mesmo WireMock: escolha de provedor, provedor padrão do cliente e
 * troca automática para o Ollama quando o Gemini cai.
 */
class ProviderRoutingIT extends AbstractIntegrationTest {

	private static final String GEMINI_CHAT = ".*/models/gemini-3\\.(8-flash|5-flash-lite):generateContent";

	static final WireMockServer APIS = new WireMockServer(options().dynamicPort());

	static {
		APIS.start();
	}

	@DynamicPropertySource
	static void providers(DynamicPropertyRegistry registry) {
		registry.add("ia-service.gemini.base-url", APIS::baseUrl);
		registry.add("spring.ai.ollama.base-url", APIS::baseUrl);
		registry.add("ia-service.ollama.enabled", () -> "true");
		registry.add("ia-service.providers.fallback", () -> "ollama");
		registry.add("resilience4j.retry.configs.default.wait-duration", () -> "10ms");
	}

	@Autowired
	private CircuitBreakerRegistry circuitBreakers;

	@Autowired
	private ProvidersHealthIndicator healthIndicator;

	private TestClient client;

	@BeforeEach
	void setUp() {
		APIS.resetAll();
		circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
		client = createClient();
		APIS.stubFor(post(urlPathEqualTo("/api/chat")).willReturn(okJson(ollamaChat("Olá do Ollama"))));
		APIS.stubFor(get(urlPathEqualTo("/api/tags")).willReturn(okJson("""
				{"models": [{"name": "llama3.2:3b", "model": "llama3.2:3b"},
				            {"name": "nomic-embed-text:latest", "model": "nomic-embed-text:latest"}]}""")));
	}

	@Test
	void shouldUseProviderRequestedByClient() throws Exception {
		chat("""
				{"message": "%s", "provider": "ollama"}""".formatted(UUID.randomUUID()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("ollama"))
				.andExpect(jsonPath("$.model").value("llama3.2:3b"))
				.andExpect(jsonPath("$.content").value("Olá do Ollama"))
				.andExpect(jsonPath("$.fallback").value(false));

		APIS.verify(0, postRequestedFor(urlPathMatching(GEMINI_CHAT)));
	}

	@Test
	void shouldFallBackToOllamaWhenGeminiIsDown() throws Exception {
		APIS.stubFor(post(urlPathMatching(GEMINI_CHAT)).willReturn(geminiError(503)));

		chat("""
				{"message": "%s"}""".formatted(UUID.randomUUID()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("ollama"))
				.andExpect(jsonPath("$.fallback").value(true));

		// Antes de trocar de provedor, o Gemini tentou o modelo principal e o reserva
		APIS.verify(postRequestedFor(urlPathMatching(".*gemini-3\\.8-flash:generateContent")));
		APIS.verify(postRequestedFor(urlPathMatching(".*gemini-3\\.5-flash-lite:generateContent")));
		APIS.verify(1, postRequestedFor(urlPathEqualTo("/api/chat")));
	}

	@Test
	void shouldNotSwitchProviderWhenOneWasRequestedExplicitly() throws Exception {
		APIS.stubFor(post(urlPathMatching(GEMINI_CHAT)).willReturn(geminiError(503)));

		chat("""
				{"message": "%s", "provider": "gemini"}""".formatted(UUID.randomUUID()))
				.andExpect(status().isServiceUnavailable());

		APIS.verify(0, postRequestedFor(urlPathEqualTo("/api/chat")));
	}

	@Test
	void shouldUseClientDefaultProvider() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.patch("/v1/admin/clients/{id}", client.id())
						.header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"defaultProvider": "ollama"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.defaultProvider").value("ollama"));

		chat("""
				{"message": "%s"}""".formatted(UUID.randomUUID()))
				.andExpect(jsonPath("$.provider").value("ollama"));
	}

	@Test
	void shouldRejectUnknownProvider() throws Exception {
		chat("""
				{"message": "Oi", "provider": "openai"}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Provedor 'openai' não está disponível. Disponíveis: gemini, ollama"));

		mockMvc.perform(MockMvcRequestBuilders.patch("/v1/admin/clients/{id}", client.id())
						.header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"defaultProvider": "openai"}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void shouldListProvidersWithHealth() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/v1/providers").header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("gemini"))
				.andExpect(jsonPath("$[0].defaultProvider").value(true))
				.andExpect(jsonPath("$[0].models.fallbackChatModel").value("gemini-3.5-flash-lite"))
				.andExpect(jsonPath("$[0].status").value("UP"))
				.andExpect(jsonPath("$[1].name").value("ollama"))
				.andExpect(jsonPath("$[1].fallbackProvider").value(true))
				.andExpect(jsonPath("$[1].models.chatModel").value("llama3.2:3b"))
				.andExpect(jsonPath("$[1].status").value("UP"));
	}

	@Test
	void shouldReportOllamaDownWhenModelIsNotPulled() throws Exception {
		APIS.stubFor(get(urlPathEqualTo("/api/tags")).willReturn(okJson("{\"models\": []}")));

		mockMvc.perform(MockMvcRequestBuilders.get("/v1/providers").header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(jsonPath("$[1].status").value("DOWN"))
				.andExpect(jsonPath("$[1].detail").value("Modelos não baixados no Ollama: llama3.2:3b, nomic-embed-text"));

		// Com o Gemini respondendo, o serviço continua saudável
		assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void shouldSendNomicTaskPrefixWhenEmbeddingWithOllama() throws Exception {
		String vector = String.join(",", Collections.nCopies(768, "0.1"));
		APIS.stubFor(post(urlPathEqualTo("/api/embed")).willReturn(okJson("""
				{"model": "nomic-embed-text", "embeddings": [[%s]]}""".formatted(vector))));

		mockMvc.perform(MockMvcRequestBuilders.post("/v1/embeddings")
						.header(ApiKeyFilter.HEADER, client.apiKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"texts": ["qual o prazo?"], "purpose": "query", "provider": "ollama"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.model").value("nomic-embed-text"))
				.andExpect(jsonPath("$.dimensions").value(768));

		APIS.verify(postRequestedFor(urlPathEqualTo("/api/embed"))
				.withRequestBody(containing("search_query: qual o prazo?")));
	}

	private ResultActions chat(String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.post("/v1/chat")
				.header(ApiKeyFilter.HEADER, client.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	/** Resposta no formato da API /api/chat do Ollama, sem streaming. */
	private static String ollamaChat(String content) {
		return """
				{"model": "llama3.2:3b", "created_at": "2026-09-25T10:00:00Z",
				 "message": {"role": "assistant", "content": "%s"},
				 "done": true, "done_reason": "stop", "prompt_eval_count": 5, "eval_count": 3}""".formatted(content);
	}

	private static ResponseDefinitionBuilder geminiError(int status) {
		return aResponse().withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"error\": {\"code\": %d, \"message\": \"erro simulado\", \"status\": \"SIMULADO\"}}"
						.formatted(status));
	}

}

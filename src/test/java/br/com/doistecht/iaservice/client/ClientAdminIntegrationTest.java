package br.com.doistecht.iaservice.client;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.doistecht.iaservice.AbstractIntegrationTest;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Ciclo de vida de um cliente: cadastro, uso da chave, rotação e desativação.
 */
class ClientAdminIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ClientRepository clientRepository;

	@BeforeEach
	void stubProvider() {
		given(gemini.chat(any(ChatCommand.class))).willReturn(new ChatResult("Olá!", "m", "gemini"));
	}

	@Test
	void shouldCreateClientAndAuthenticateWithGeneratedKey() throws Exception {
		String name = "portal-" + UUID.randomUUID();
		String response = mockMvc.perform(post("/v1/admin/clients")
						.header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "%s"}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.apiKey", startsWith("dtia_")))
				.andExpect(jsonPath("$.client.rateLimitPerMinute").value(10))
				.andExpect(jsonPath("$.client.dailyQuota").value(200))
				.andReturn().getResponse().getContentAsString();
		String apiKey = JsonPath.read(response, "$.apiKey");
		Integer id = JsonPath.read(response, "$.client.id");

		chat(apiKey).andExpect(status().isOk());

		// A chave em texto puro nunca é gravada, apenas o hash
		Client stored = clientRepository.findById(id.longValue()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(stored.getApiKeyHash())
				.isEqualTo(ApiKey.hash(apiKey))
				.isNotEqualTo(apiKey);

		mockMvc.perform(get("/v1/admin/clients/{id}", id).header(ApiKeyFilter.HEADER, ADMIN_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.apiKey").doesNotExist())
				.andExpect(jsonPath("$.apiKeyPrefix").value(apiKey.substring(0, 12)));
	}

	@Test
	void shouldInvalidateOldKeyAfterRotation() throws Exception {
		TestClient client = createClient();
		chat(client.apiKey()).andExpect(status().isOk());

		String response = mockMvc.perform(post("/v1/admin/clients/{id}/rotate-key", client.id())
						.header(ApiKeyFilter.HEADER, ADMIN_KEY))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String newKey = JsonPath.read(response, "$.apiKey");

		chat(client.apiKey()).andExpect(status().isUnauthorized());
		chat(newKey).andExpect(status().isOk());
	}

	@Test
	void shouldRejectDeactivatedClientAndAllowReactivation() throws Exception {
		TestClient client = createClient();

		setActive(client, false).andExpect(jsonPath("$.active").value(false));
		chat(client.apiKey()).andExpect(status().isUnauthorized());

		setActive(client, true).andExpect(jsonPath("$.active").value(true));
		chat(client.apiKey()).andExpect(status().isOk());
	}

	@Test
	void shouldRejectDuplicateClientName() throws Exception {
		String body = """
				{"name": "duplicado-%s"}
				""".formatted(UUID.randomUUID());
		mockMvc.perform(post("/v1/admin/clients").header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/v1/admin/clients").header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict());
	}

	@Test
	void shouldReturnNotFoundForUnknownClient() throws Exception {
		mockMvc.perform(get("/v1/admin/clients/999999").header(ApiKeyFilter.HEADER, ADMIN_KEY))
				.andExpect(status().isNotFound());
	}

	private ResultActions chat(String apiKey) throws Exception {
		return mockMvc.perform(post("/v1/chat")
				.header(ApiKeyFilter.HEADER, apiKey)
				.header("Cache-Control", "no-cache")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"message": "Oi"}
						"""));
	}

	private ResultActions setActive(TestClient client, boolean active) throws Exception {
		return mockMvc.perform(patch("/v1/admin/clients/{id}", client.id())
						.header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"active": %s}
								""".formatted(active)))
				.andExpect(status().isOk());
	}

}

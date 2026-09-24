package br.com.doistecht.iaservice.template;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;

/**
 * Fluxo completo com banco real: migrations, seeds, versionamento, templates por cliente
 * e execução de tarefas.
 */
class TemplateAdminIntegrationTest extends AbstractIntegrationTest {

	@Test
	void shouldLoadSeedTemplatesFromMigrations() throws Exception {
		mockMvc.perform(get("/v1/admin/templates").header(ApiKeyFilter.HEADER, ADMIN_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].name",
						hasItems("resumir-texto", "classificar-ticket", "gerar-descricao-produto")));

		mockMvc.perform(get("/v1/admin/templates/classificar-ticket").header(ApiKeyFilter.HEADER, ADMIN_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].variables[0]").value("ticket"))
				.andExpect(jsonPath("$[0].outputSchema.required", hasItems("categoria", "prioridade", "resumo")));
	}

	@Test
	void shouldRequireAdminKeyForAdminRoutes() throws Exception {
		TestClient client = createClient();

		mockMvc.perform(get("/v1/admin/templates").header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldCreateVersionsAndExecuteLatestActive() throws Exception {
		TestClient client = createClient();
		String name = "traduzir-" + UUID.randomUUID();
		String body = """
				{"name": "%s", "userPromptTemplate": "Traduza para %s: {texto}"}
				""";
		mockMvc.perform(post("/v1/admin/templates")
						.header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body.formatted(name, "inglês")))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(post("/v1/admin/templates")
						.header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body.formatted(name, "espanhol")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.version").value(2));

		given(gemini.chat(any(ChatCommand.class))).willReturn(new ChatResult("Hola", "m", "gemini"));

		executeTask(client, name, "").andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(2))
				.andExpect(jsonPath("$.content").value("Hola"));

		// Desativando a v2, a versão ativa mais recente passa a ser a v1
		mockMvc.perform(patch("/v1/admin/templates/{name}/versions/2", name)
						.header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"active": false}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		executeTask(client, name, "").andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1));

		executeTask(client, name, "?version=2").andExpect(status().isNotFound());
	}

	@Test
	void shouldPreferClientTemplateOverGlobalOne() throws Exception {
		TestClient owner = createClient();
		TestClient other = createClient();
		String name = "saudacao-" + UUID.randomUUID();

		createTemplate(null, name, "Global: {texto}");
		createTemplate(owner.id(), name, "Do cliente: {texto}");

		given(gemini.chat(any(ChatCommand.class))).willReturn(new ChatResult("ok", "m", "gemini"));
		ArgumentCaptor<ChatCommand> captor = ArgumentCaptor.forClass(ChatCommand.class);

		executeTask(owner, name, "").andExpect(status().isOk());
		Mockito.verify(gemini, Mockito.atLeastOnce()).chat(captor.capture());
		org.assertj.core.api.Assertions.assertThat(captor.getValue().message()).isEqualTo("Do cliente: Olá");

		executeTask(other, name, "").andExpect(status().isOk());
		Mockito.verify(gemini, Mockito.atLeast(2)).chat(captor.capture());
		org.assertj.core.api.Assertions.assertThat(captor.getValue().message()).isEqualTo("Global: Olá");
	}

	@Test
	void shouldExecuteStructuredSeedTemplate() throws Exception {
		TestClient client = createClient();
		given(gemini.structured(any(ChatCommand.class), anyString())).willReturn(new ChatResult(
				"{\"categoria\": \"financeiro\", \"prioridade\": \"alta\", \"resumo\": \"Cobrança duplicada\"}",
				"m", "gemini"));

		mockMvc.perform(post("/v1/tasks/classificar-ticket")
						.header(ApiKeyFilter.HEADER, client.apiKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"variables": {"ticket": "Fui cobrado duas vezes este mês!"}}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.categoria").value("financeiro"))
				.andExpect(jsonPath("$.content").doesNotExist());
	}

	@Test
	void shouldRejectTemplateForUnknownClient() throws Exception {
		mockMvc.perform(post("/v1/admin/templates")
						.header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"clientId": 999999, "name": "qualquer", "userPromptTemplate": "x"}
								"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void shouldRejectTemplateWithInvalidSchema() throws Exception {
		mockMvc.perform(post("/v1/admin/templates")
						.header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "schema-ruim", "userPromptTemplate": "x", "outputSchema": [1, 2]}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void shouldRejectInvalidTemplateName() throws Exception {
		mockMvc.perform(post("/v1/admin/templates")
						.header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Nome Inválido", "userPromptTemplate": "x"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.name").exists());
	}

	private void createTemplate(Long clientId, String name, String userPrompt) throws Exception {
		mockMvc.perform(post("/v1/admin/templates")
						.header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"clientId": %s, "name": "%s", "userPromptTemplate": "%s"}
								""".formatted(clientId, name, userPrompt)))
				.andExpect(status().isCreated());
	}

	private org.springframework.test.web.servlet.ResultActions executeTask(TestClient client, String name,
			String query) throws Exception {
		return mockMvc.perform(post("/v1/tasks/" + name + query)
				.header(ApiKeyFilter.HEADER, client.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"variables": {"texto": "Olá"}}
						"""));
	}

}

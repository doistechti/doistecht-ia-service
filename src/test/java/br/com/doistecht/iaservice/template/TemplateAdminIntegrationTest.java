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

import br.com.doistecht.iaservice.AbstractPostgresIntegrationTest;
import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fluxo completo com banco real: migrations, seeds, versionamento e execução de tarefas.
 * Apenas o provedor de IA é simulado.
 */
@AutoConfigureMockMvc
class TemplateAdminIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AiProvider aiProvider;

	@Test
	void shouldLoadSeedTemplatesFromMigrations() throws Exception {
		mockMvc.perform(get("/v1/admin/templates").header(ApiKeyFilter.HEADER, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].name",
						hasItems("resumir-texto", "classificar-ticket", "gerar-descricao-produto")));

		mockMvc.perform(get("/v1/admin/templates/classificar-ticket").header(ApiKeyFilter.HEADER, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].variables[0]").value("ticket"))
				.andExpect(jsonPath("$[0].outputSchema.required", hasItems("categoria", "prioridade", "resumo")));
	}

	@Test
	void shouldCreateVersionsAndExecuteLatestActive() throws Exception {
		String body = """
				{"name": "traduzir-texto", "userPromptTemplate": "Traduza para %s: {texto}"}
				""";
		mockMvc.perform(post("/v1/admin/templates")
						.header(ApiKeyFilter.HEADER, API_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body.formatted("inglês")))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(post("/v1/admin/templates")
						.header(ApiKeyFilter.HEADER, API_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body.formatted("espanhol")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.version").value(2));

		given(aiProvider.chat(any(ChatCommand.class))).willReturn(new ChatResult("Hola", "m", "gemini"));

		mockMvc.perform(post("/v1/tasks/traduzir-texto")
						.header(ApiKeyFilter.HEADER, API_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"variables": {"texto": "Olá"}}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(2))
				.andExpect(jsonPath("$.content").value("Hola"));

		// Desativando a v2, a versão ativa mais recente passa a ser a v1
		mockMvc.perform(patch("/v1/admin/templates/traduzir-texto/versions/2")
						.header(ApiKeyFilter.HEADER, API_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"active": false}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(post("/v1/tasks/traduzir-texto")
						.header(ApiKeyFilter.HEADER, API_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"variables": {"texto": "Olá"}}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(post("/v1/tasks/traduzir-texto?version=2")
						.header(ApiKeyFilter.HEADER, API_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"variables": {"texto": "Olá"}}
								"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void shouldExecuteStructuredSeedTemplate() throws Exception {
		given(aiProvider.structured(any(ChatCommand.class), anyString())).willReturn(new ChatResult(
				"{\"categoria\": \"financeiro\", \"prioridade\": \"alta\", \"resumo\": \"Cobrança duplicada\"}",
				"m", "gemini"));

		mockMvc.perform(post("/v1/tasks/classificar-ticket")
						.header(ApiKeyFilter.HEADER, API_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"variables": {"ticket": "Fui cobrado duas vezes este mês!"}}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.categoria").value("financeiro"))
				.andExpect(jsonPath("$.content").doesNotExist());
	}

	@Test
	void shouldRejectTemplateWithInvalidSchema() throws Exception {
		mockMvc.perform(post("/v1/admin/templates")
						.header(ApiKeyFilter.HEADER, API_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "schema-ruim", "userPromptTemplate": "x", "outputSchema": [1, 2]}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void shouldRejectInvalidTemplateName() throws Exception {
		mockMvc.perform(post("/v1/admin/templates")
						.header(ApiKeyFilter.HEADER, API_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Nome Inválido", "userPromptTemplate": "x"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.name").exists());
	}

}

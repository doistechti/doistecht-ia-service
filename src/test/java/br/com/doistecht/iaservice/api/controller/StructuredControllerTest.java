package br.com.doistecht.iaservice.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.exception.GlobalExceptionHandler;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import br.com.doistecht.iaservice.structured.InvalidSchemaException;
import br.com.doistecht.iaservice.structured.InvalidStructuredOutputException;
import br.com.doistecht.iaservice.structured.StructuredOutputService;
import br.com.doistecht.iaservice.structured.StructuredResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(StructuredController.class)
@Import({ ApiKeyFilter.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(IaServiceProperties.class)
@TestPropertySource(properties = "ia-service.api-key=test-api-key")
class StructuredControllerTest {

	private static final String VALID_KEY = "test-api-key";

	private static final String BODY = """
			{
			  "input": "João Silva, 32 anos",
			  "schema": {"type": "object", "properties": {"nome": {"type": "string"}}}
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StructuredOutputService structuredOutputService;

	@Test
	void shouldReturnStructuredData() throws Exception {
		JsonNode data = JsonMapper.builder().build().readTree("{\"nome\": \"João Silva\"}");
		given(structuredOutputService.generate(any(ChatCommand.class), any(JsonNode.class)))
				.willReturn(new StructuredResult(data, "gemini-2.5-flash", "gemini"));

		mockMvc.perform(post("/v1/structured")
						.header(ApiKeyFilter.HEADER, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nome").value("João Silva"))
				.andExpect(jsonPath("$.provider").value("gemini"));
	}

	@Test
	void shouldRequireSchema() throws Exception {
		mockMvc.perform(post("/v1/structured")
						.header(ApiKeyFilter.HEADER, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"input": "abc"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.schema").exists());
	}

	@Test
	void shouldReturnBadRequestForInvalidSchema() throws Exception {
		given(structuredOutputService.generate(any(ChatCommand.class), any(JsonNode.class)))
				.willThrow(new InvalidSchemaException("Schema inválido"));

		mockMvc.perform(post("/v1/structured")
						.header(ApiKeyFilter.HEADER, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isBadRequest());
	}

	@Test
	void shouldReturnUnprocessableWhenModelOutputIsInvalid() throws Exception {
		given(structuredOutputService.generate(any(ChatCommand.class), any(JsonNode.class)))
				.willThrow(new InvalidStructuredOutputException(List.of("$.nome: obrigatório")));

		mockMvc.perform(post("/v1/structured")
						.header(ApiKeyFilter.HEADER, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.errors[0]").value("$.nome: obrigatório"));
	}

}

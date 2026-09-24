package br.com.doistecht.iaservice.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.exception.GlobalExceptionHandler;
import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
@Import({ ApiKeyFilter.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(IaServiceProperties.class)
@TestPropertySource(properties = "ia-service.api-key=test-api-key")
class ChatControllerTest {

	private static final String VALID_KEY = "test-api-key";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AiProvider aiProvider;

	@Test
	void shouldReturnModelResponse() throws Exception {
		given(aiProvider.chat(any(ChatCommand.class)))
				.willReturn(new ChatResult("Olá!", "gemini-2.5-flash", "gemini"));

		mockMvc.perform(post("/v1/chat")
						.header(ApiKeyFilter.HEADER, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Oi", "systemPrompt": "Seja breve."}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("Olá!"))
				.andExpect(jsonPath("$.model").value("gemini-2.5-flash"))
				.andExpect(jsonPath("$.provider").value("gemini"));
	}

	@Test
	void shouldRejectMissingApiKey() throws Exception {
		mockMvc.perform(post("/v1/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Oi"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void shouldRejectInvalidApiKey() throws Exception {
		mockMvc.perform(post("/v1/chat")
						.header(ApiKeyFilter.HEADER, "chave-errada")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Oi"}
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldRejectBlankMessage() throws Exception {
		mockMvc.perform(post("/v1/chat")
						.header(ApiKeyFilter.HEADER, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "  "}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.message").exists());
	}

	@Test
	void shouldReturnBadGatewayWhenProviderFails() throws Exception {
		given(aiProvider.chat(any(ChatCommand.class)))
				.willThrow(new AiProviderException("gemini", "falhou", null));

		mockMvc.perform(post("/v1/chat")
						.header(ApiKeyFilter.HEADER, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Oi"}
								"""))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.provider").value("gemini"));
	}

}

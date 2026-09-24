package br.com.doistecht.iaservice.api.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.exception.GlobalExceptionHandler;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import br.com.doistecht.iaservice.task.TaskResult;
import br.com.doistecht.iaservice.task.TaskService;
import br.com.doistecht.iaservice.template.MissingVariablesException;
import br.com.doistecht.iaservice.template.TemplateNotFoundException;
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

@WebMvcTest(TaskController.class)
@Import({ ApiKeyFilter.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(IaServiceProperties.class)
@TestPropertySource(properties = "ia-service.api-key=test-api-key")
class TaskControllerTest {

	private static final String VALID_KEY = "test-api-key";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TaskService taskService;

	@Test
	void shouldExecuteTextTask() throws Exception {
		given(taskService.execute(eq("resumir-texto"), isNull(), anyMap()))
				.willReturn(new TaskResult("resumir-texto", 1, "Resumo", null, "gemini-2.5-flash", "gemini"));

		mockMvc.perform(post("/v1/tasks/resumir-texto")
						.header(ApiKeyFilter.HEADER, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"variables": {"texto": "abc", "linhas": "2"}}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.template").value("resumir-texto"))
				.andExpect(jsonPath("$.content").value("Resumo"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void shouldPassRequestedVersion() throws Exception {
		given(taskService.execute(eq("resumir-texto"), eq(3), anyMap()))
				.willReturn(new TaskResult("resumir-texto", 3, "Resumo", null, "m", "gemini"));

		mockMvc.perform(post("/v1/tasks/resumir-texto?version=3")
						.header(ApiKeyFilter.HEADER, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(3));
	}

	@Test
	void shouldReturnNotFoundForUnknownTemplate() throws Exception {
		given(taskService.execute(eq("nao-existe"), isNull(), anyMap()))
				.willThrow(new TemplateNotFoundException("nao-existe", null));

		mockMvc.perform(post("/v1/tasks/nao-existe")
						.header(ApiKeyFilter.HEADER, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void shouldReturnBadRequestWithMissingVariables() throws Exception {
		given(taskService.execute(eq("resumir-texto"), isNull(), anyMap()))
				.willThrow(new MissingVariablesException(List.of("texto")));

		mockMvc.perform(post("/v1/tasks/resumir-texto")
						.header(ApiKeyFilter.HEADER, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.missingVariables[0]").value("texto"));
	}

}

package br.com.doistecht.iaservice.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.structured.StructuredOutputService;
import br.com.doistecht.iaservice.structured.StructuredResult;
import br.com.doistecht.iaservice.template.MissingVariablesException;
import br.com.doistecht.iaservice.template.PromptTemplate;
import br.com.doistecht.iaservice.template.PromptTemplateService;
import br.com.doistecht.iaservice.template.TemplateRenderer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class TaskServiceTest {

	private final ObjectMapper mapper = JsonMapper.builder().build();

	private final PromptTemplateService templateService = mock(PromptTemplateService.class);

	private final AiProvider aiProvider = mock(AiProvider.class);

	private final StructuredOutputService structuredOutputService = mock(StructuredOutputService.class);

	private final TaskService taskService = new TaskService(templateService, new TemplateRenderer(), aiProvider,
			structuredOutputService, mapper);

	@Test
	void shouldRenderTemplateAndReturnText() {
		given(templateService.resolve("resumir-texto", null)).willReturn(
				new PromptTemplate("resumir-texto", 2, "Seja fiel ao texto.", "Resuma em {linhas} linhas: {texto}",
						null));
		given(aiProvider.chat(any(ChatCommand.class))).willReturn(new ChatResult("Resumo", "m", "gemini"));

		TaskResult result = taskService.execute("resumir-texto", null, Map.of("linhas", "2", "texto", "Abc"));

		ArgumentCaptor<ChatCommand> captor = ArgumentCaptor.forClass(ChatCommand.class);
		verify(aiProvider).chat(captor.capture());
		assertThat(captor.getValue().message()).isEqualTo("Resuma em 2 linhas: Abc");
		assertThat(captor.getValue().systemPrompt()).isEqualTo("Seja fiel ao texto.");
		assertThat(result.content()).isEqualTo("Resumo");
		assertThat(result.version()).isEqualTo(2);
		assertThat(result.data()).isNull();
	}

	@Test
	void shouldUseStructuredOutputWhenTemplateHasSchema() {
		String schema = "{\"type\": \"object\"}";
		given(templateService.resolve("classificar-ticket", 1)).willReturn(
				new PromptTemplate("classificar-ticket", 1, null, "Classifique: {ticket}", schema));
		JsonNode data = mapper.readTree("{\"categoria\": \"financeiro\"}");
		given(structuredOutputService.generate(any(ChatCommand.class), eq(mapper.readTree(schema))))
				.willReturn(new StructuredResult(data, "m", "gemini"));

		TaskResult result = taskService.execute("classificar-ticket", 1, Map.of("ticket", "Cobrança duplicada"));

		assertThat(result.data()).isEqualTo(data);
		assertThat(result.content()).isNull();
		verifyNoInteractions(aiProvider);
	}

	@Test
	void shouldRejectMissingVariablesBeforeCallingProvider() {
		given(templateService.resolve("resumir-texto", null)).willReturn(
				new PromptTemplate("resumir-texto", 1, "Sistema {tom}", "Resuma: {texto}", null));

		assertThatThrownBy(() -> taskService.execute("resumir-texto", null, Map.of()))
				.isInstanceOf(MissingVariablesException.class)
				.extracting(ex -> ((MissingVariablesException) ex).getMissing())
				.asList()
				.containsExactly("tom", "texto");
		verifyNoInteractions(aiProvider, structuredOutputService);
	}

}

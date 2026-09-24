package br.com.doistecht.iaservice.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class StructuredOutputServiceTest {

	private final ObjectMapper mapper = JsonMapper.builder().build();

	private final AiProvider aiProvider = mock(AiProvider.class);

	private final StructuredOutputService service =
			new StructuredOutputService(aiProvider, new JsonSchemaValidator(), mapper);

	private final ChatCommand command = new ChatCommand(null, "João, 32 anos");

	private final JsonNode schema = mapper.readTree("""
			{"type": "object", "properties": {"nome": {"type": "string"}, "idade": {"type": "integer"}},
			 "required": ["nome", "idade"]}""");

	@Test
	void shouldReturnValidatedJson() {
		given(aiProvider.structured(any(ChatCommand.class), anyString()))
				.willReturn(result("{\"nome\": \"João\", \"idade\": 32}"));

		StructuredResult result = service.generate(command, schema);

		assertThat(result.data().get("idade").asInt()).isEqualTo(32);
		assertThat(result.provider()).isEqualTo("gemini");
		verify(aiProvider, times(1)).structured(any(ChatCommand.class), anyString());
	}

	@Test
	void shouldRetryOnceWhenOutputDoesNotMatchSchema() {
		given(aiProvider.structured(any(ChatCommand.class), anyString()))
				.willReturn(result("{\"nome\": \"João\"}"))
				.willReturn(result("{\"nome\": \"João\", \"idade\": 32}"));

		StructuredResult result = service.generate(command, schema);

		assertThat(result.data().get("nome").asString()).isEqualTo("João");
		verify(aiProvider, times(2)).structured(any(ChatCommand.class), anyString());
	}

	@Test
	void shouldFailAfterSecondInvalidOutput() {
		given(aiProvider.structured(any(ChatCommand.class), anyString()))
				.willReturn(result("isto não é JSON"));

		assertThatThrownBy(() -> service.generate(command, schema))
				.isInstanceOf(InvalidStructuredOutputException.class);
		verify(aiProvider, times(StructuredOutputService.MAX_ATTEMPTS)).structured(any(ChatCommand.class),
				anyString());
	}

	@Test
	void shouldNotCallProviderWhenSchemaIsInvalid() {
		JsonNode invalid = mapper.readTree("\"não é um objeto\"");

		assertThatThrownBy(() -> service.generate(command, invalid))
				.isInstanceOf(InvalidSchemaException.class);
		verify(aiProvider, never()).structured(any(ChatCommand.class), anyString());
	}

	private static ChatResult result(String content) {
		return new ChatResult(content, "gemini-2.5-flash", "gemini");
	}

}

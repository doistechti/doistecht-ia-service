package br.com.doistecht.iaservice.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatMessage;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.StreamChunk;
import br.com.doistecht.iaservice.ratelimit.RateLimitExceededException;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

@WebMvcTest(ChatController.class)
class ChatControllerTest extends ApiControllerTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AiProvider aiProvider;

	@Test
	void shouldReturnModelResponse() throws Exception {
		given(aiProvider.chat(any(ChatCommand.class)))
				.willReturn(new ChatResult("Olá!", "gemini-2.5-flash", "gemini"));

		mockMvc.perform(post("/v1/chat")
						.header(ApiKeyFilter.HEADER, CLIENT_KEY)
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
	void shouldForwardConversationHistory() throws Exception {
		given(aiProvider.chat(any(ChatCommand.class)))
				.willReturn(new ChatResult("Brasília tem cerca de 3 milhões.", "gemini-2.5-flash", "gemini"));

		mockMvc.perform(post("/v1/chat")
						.header(ApiKeyFilter.HEADER, CLIENT_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "message": "E qual a população dela?",
								  "history": [
								    {"role": "user", "content": "Qual a capital do Brasil?"},
								    {"role": "assistant", "content": "Brasília."}
								  ]
								}
								"""))
				.andExpect(status().isOk());

		ArgumentCaptor<ChatCommand> captor = ArgumentCaptor.forClass(ChatCommand.class);
		verify(aiProvider).chat(captor.capture());
		assertThat(captor.getValue().history()).containsExactly(
				new ChatMessage(ChatMessage.Role.USER, "Qual a capital do Brasil?"),
				new ChatMessage(ChatMessage.Role.ASSISTANT, "Brasília."));
	}

	@Test
	void shouldRejectInvalidHistoryRole() throws Exception {
		mockMvc.perform(post("/v1/chat")
						.header(ApiKeyFilter.HEADER, CLIENT_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Oi", "history": [{"role": "system", "content": "x"}]}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['history[0].role']").exists());
	}

	@Test
	void shouldStreamChunksAsServerSentEvents() throws Exception {
		given(aiProvider.chatStream(any(ChatCommand.class)))
				.willReturn(Flux.just(new StreamChunk("Olá"), new StreamChunk(""), new StreamChunk(", mundo\n!")));

		MvcResult result = mockMvc.perform(post("/v1/chat/stream")
						.header(ApiKeyFilter.HEADER, CLIENT_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Oi"}
								"""))
				.andExpect(request().asyncStarted())
				.andReturn();

		String body = mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(body)
				.contains("event:message\ndata:{\"content\":\"Olá\"}")
				.contains("data:{\"content\":\", mundo\\n!\"}")
				.contains("event:done")
				.doesNotContain("data:{\"content\":\"\"}");
	}

	@Test
	void shouldEmitErrorEventWhenStreamFails() throws Exception {
		given(aiProvider.chatStream(any(ChatCommand.class)))
				.willReturn(Flux.concat(Flux.just(new StreamChunk("Parte")),
						Flux.error(new AiProviderException("gemini", "falhou", null))));

		MvcResult result = mockMvc.perform(post("/v1/chat/stream")
						.header(ApiKeyFilter.HEADER, CLIENT_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Oi"}
								"""))
				.andExpect(request().asyncStarted())
				.andReturn();

		String body = mockMvc.perform(asyncDispatch(result))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains("data:{\"content\":\"Parte\"}").contains("event:error").doesNotContain("event:done");
	}

	@Test
	void shouldReturnTooManyRequestsWhenRateLimitIsExceeded() throws Exception {
		given(rateLimitService.consume(CLIENT)).willThrow(
				new RateLimitExceededException(RateLimitExceededException.Limit.PER_MINUTE, 12));

		mockMvc.perform(post("/v1/chat")
						.header(ApiKeyFilter.HEADER, CLIENT_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Oi"}
								"""))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "12"))
				.andExpect(jsonPath("$.limit").value("PER_MINUTE"));
		verify(aiProvider, never()).chat(any(ChatCommand.class));
	}

	@Test
	void shouldExposeRemainingLimitsInHeaders() throws Exception {
		given(aiProvider.chat(any(ChatCommand.class))).willReturn(new ChatResult("Olá!", "m", "gemini"));

		mockMvc.perform(post("/v1/chat")
						.header(ApiKeyFilter.HEADER, CLIENT_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Oi"}
								"""))
				.andExpect(status().isOk())
				.andExpect(header().string("X-RateLimit-Remaining", "9"))
				.andExpect(header().string("X-Quota-Remaining", "199"));
	}

	@Test
	void shouldNotAcceptAdminKeyOnClientRoutes() throws Exception {
		mockMvc.perform(post("/v1/chat")
						.header(ApiKeyFilter.HEADER, ADMIN_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Oi"}
								"""))
				.andExpect(status().isUnauthorized());
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
						.header(ApiKeyFilter.HEADER, CLIENT_KEY)
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
						.header(ApiKeyFilter.HEADER, CLIENT_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message": "Oi"}
								"""))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.provider").value("gemini"));
	}

}

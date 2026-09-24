package br.com.doistecht.iaservice.ratelimit;

import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.doistecht.iaservice.AbstractMockedProviderIT;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Rate limit e cota com Redis real (Bucket4j + contador diário).
 */
class RateLimitIT extends AbstractMockedProviderIT {

	@BeforeEach
	void stubProvider() {
		given(gemini.chat(any(ChatCommand.class))).willReturn(new ChatResult("Olá!", "m", "gemini"));
	}

	@Test
	void shouldLimitRequestsPerMinute() throws Exception {
		TestClient client = createClient(2, 100);

		chat(client).andExpect(status().isOk())
				.andExpect(header().string(RateLimitInterceptor.LIMIT_HEADER, "2"))
				.andExpect(header().string(RateLimitInterceptor.REMAINING_HEADER, "1"));
		chat(client).andExpect(status().isOk())
				.andExpect(header().string(RateLimitInterceptor.REMAINING_HEADER, "0"));

		chat(client).andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.limit").value("PER_MINUTE"))
				.andExpect(jsonPath("$.retryAfterSeconds", greaterThan(0)));
	}

	@Test
	void shouldEnforceDailyQuota() throws Exception {
		TestClient client = createClient(100, 2);

		chat(client).andExpect(status().isOk())
				.andExpect(header().string(RateLimitInterceptor.QUOTA_REMAINING_HEADER, "1"));
		chat(client).andExpect(status().isOk())
				.andExpect(header().string(RateLimitInterceptor.QUOTA_REMAINING_HEADER, "0"));

		chat(client).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.limit").value("DAILY_QUOTA"));
	}

	@Test
	void shouldKeepLimitsSeparatePerClient() throws Exception {
		TestClient first = createClient(1, 100);
		TestClient second = createClient(1, 100);

		chat(first).andExpect(status().isOk());
		chat(first).andExpect(status().isTooManyRequests());

		chat(second).andExpect(status().isOk());
	}

	@Test
	void shouldNotConsumeLimitOnNonAiRoutes() throws Exception {
		TestClient client = createClient(1, 100);

		mockMvc.perform(get("/v1/usage").header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(status().isOk());
		mockMvc.perform(get("/v1/usage").header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(status().isOk());

		chat(client).andExpect(status().isOk());
	}

	private ResultActions chat(TestClient client) throws Exception {
		return mockMvc.perform(post("/v1/chat")
				.header(ApiKeyFilter.HEADER, client.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"message": "Oi"}
						"""));
	}

}

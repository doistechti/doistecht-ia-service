package br.com.doistecht.iaservice.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.doistecht.iaservice.client.AuthenticatedClient;
import br.com.doistecht.iaservice.config.TestProperties;
import br.com.doistecht.iaservice.metrics.GatewayMetrics;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.EmbeddingPurpose;
import br.com.doistecht.iaservice.provider.EmbeddingResult;
import br.com.doistecht.iaservice.provider.StreamChunk;
import br.com.doistecht.iaservice.provider.TokenUsage;
import br.com.doistecht.iaservice.provider.gemini.GeminiProvider;
import br.com.doistecht.iaservice.structured.JsonSchemaValidator;
import br.com.doistecht.iaservice.usage.UsageEvent;
import br.com.doistecht.iaservice.usage.UsageRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

class GatewayAiProviderTest {

	private static final String SCHEMA = "{\"type\": \"object\", \"required\": [\"nome\"]}";

	private final GeminiProvider delegate = mock(GeminiProvider.class);

	private final ResponseCache cache = mock(ResponseCache.class);

	private final UsageRecorder usageRecorder = mock(UsageRecorder.class);

	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

	private final GatewayAiProvider gateway = new GatewayAiProvider(
			new ProviderRouter(List.of(delegate), TestProperties.defaults()), cache, usageRecorder,
			new JsonSchemaValidator(), JsonMapper.builder().build(), new GatewayMetrics(meterRegistry));

	private final ChatCommand command = new ChatCommand(null, "Oi");

	private MockHttpServletRequest request;

	@BeforeEach
	void setUp() {
		given(delegate.name()).willReturn("gemini");
		given(cache.key(any(), anyString(), anyString(), any(), any())).willReturn("chave");
		given(cache.get("chave")).willReturn(Optional.empty());

		request = new MockHttpServletRequest("POST", "/v1/chat");
		request.setAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE, new AuthenticatedClient(7L, "portal", 10, 200));
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	@AfterEach
	void tearDown() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void shouldCallProviderCacheResultAndRecordUsage() {
		ChatResult result = new ChatResult("Olá", "gemini-2.5-flash", "gemini", new TokenUsage(5, 3));
		given(delegate.chat(command)).willReturn(result);

		assertThat(gateway.chat(command)).isEqualTo(result);

		verify(cache).put("chave", result);
		UsageEvent event = recordedEvent();
		assertThat(event.clientId()).isEqualTo(7L);
		assertThat(event.endpoint()).isEqualTo("/v1/chat");
		assertThat(event.usage()).isEqualTo(new TokenUsage(5, 3));
		assertThat(event.cacheHit()).isFalse();
		assertThat(event.success()).isTrue();
	}

	@Test
	void shouldServeFromCacheWithoutCallingProvider() {
		given(cache.get("chave")).willReturn(Optional.of(new ChatResult("Do cache", "m", "gemini")));

		assertThat(gateway.chat(command).content()).isEqualTo("Do cache");

		verify(delegate, never()).chat(any());
		UsageEvent event = recordedEvent();
		assertThat(event.cacheHit()).isTrue();
		assertThat(event.usage()).isNull();
	}

	@Test
	void shouldBypassCacheWhenClientSendsNoCache() {
		request.addHeader("Cache-Control", "no-cache");
		given(delegate.chat(command)).willReturn(new ChatResult("Novo", "m", "gemini"));

		assertThat(gateway.chat(command).content()).isEqualTo("Novo");

		verify(cache, never()).get(anyString());
	}

	@Test
	void shouldRecordFailureAndRethrow() {
		given(delegate.chat(command)).willThrow(new AiProviderException("gemini", "falhou", null));

		assertThatThrownBy(() -> gateway.chat(command)).isInstanceOf(AiProviderException.class);

		assertThat(recordedEvent().success()).isFalse();
		verify(cache, never()).put(anyString(), any());
	}

	@Test
	void shouldNotCacheStructuredResponseThatDoesNotMatchSchema() {
		given(delegate.structured(command, SCHEMA)).willReturn(new ChatResult("{\"outro\": 1}", "m", "gemini"));

		gateway.structured(command, SCHEMA);

		verify(cache, never()).put(anyString(), any());
	}

	@Test
	void shouldCacheStructuredResponseThatMatchesSchema() {
		ChatResult valid = new ChatResult("{\"nome\": \"Ana\"}", "m", "gemini");
		given(delegate.structured(command, SCHEMA)).willReturn(valid);

		gateway.structured(command, SCHEMA);

		verify(cache).put("chave", valid);
	}

	@Test
	void shouldRecordStreamUsageWhenStreamCompletes() {
		given(delegate.chatStream(command)).willReturn(Flux.just(
				new StreamChunk("Olá", "gemini-2.5-flash", null),
				new StreamChunk("", "gemini-2.5-flash", new TokenUsage(4, 2))));

		assertThat(gateway.chatStream(command).collectList().block()).hasSize(2);

		UsageEvent event = recordedEvent();
		assertThat(event.operation()).isEqualTo(UsageEvent.Operation.STREAM);
		assertThat(event.usage()).isEqualTo(new TokenUsage(4, 2));
		assertThat(event.model()).isEqualTo("gemini-2.5-flash");
		verifyNoInteractions(cache);
	}

	@Test
	void shouldRecordEmbeddingUsage() {
		given(delegate.embed(List.of("texto"), EmbeddingPurpose.DOCUMENT))
				.willReturn(new EmbeddingResult(List.of(new float[] { 1f }), "gemini-embedding-001", null));

		gateway.embed(List.of("texto"), EmbeddingPurpose.DOCUMENT, null);

		UsageEvent event = recordedEvent();
		assertThat(event.operation()).isEqualTo(UsageEvent.Operation.EMBEDDING);
		assertThat(event.model()).isEqualTo("gemini-embedding-001");
		verifyNoInteractions(cache);
	}

	@Test
	void shouldAttributeBackgroundCallsToInformedClient() {
		RequestContextHolder.resetRequestAttributes();
		given(delegate.embed(List.of("texto"), EmbeddingPurpose.DOCUMENT))
				.willReturn(new EmbeddingResult(List.of(new float[] { 1f }), "gemini-embedding-001", null));

		UsageAttribution.runAs(42L, "portal", "/v1/documents",
				() -> gateway.embed(List.of("texto"), EmbeddingPurpose.DOCUMENT, null));

		UsageEvent event = recordedEvent();
		assertThat(event.clientId()).isEqualTo(42L);
		assertThat(event.endpoint()).isEqualTo("/v1/documents");
	}

	@Test
	void shouldCountCallsInMetrics() {
		given(delegate.chat(command)).willReturn(new ChatResult("Olá", "m", "gemini", new TokenUsage(5, 3)));

		gateway.chat(command);

		assertThat(meterRegistry.get(GatewayMetrics.CALLS).tag("client", "portal").tag("outcome", "success")
				.counter().count()).isEqualTo(1.0);
		assertThat(meterRegistry.get(GatewayMetrics.TOKENS).tag("type", "output").counter().count()).isEqualTo(3.0);
	}

	@Test
	void shouldNotRecordUsageOutsideClientRequest() {
		RequestContextHolder.resetRequestAttributes();
		given(delegate.chat(command)).willReturn(new ChatResult("Olá", "m", "gemini"));

		gateway.chat(command);

		verifyNoInteractions(usageRecorder);
	}

	private UsageEvent recordedEvent() {
		ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
		verify(usageRecorder).record(captor.capture());
		return captor.getValue();
	}

}

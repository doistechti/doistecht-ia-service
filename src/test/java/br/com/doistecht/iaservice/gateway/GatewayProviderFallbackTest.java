package br.com.doistecht.iaservice.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.config.TestProperties;
import br.com.doistecht.iaservice.metrics.GatewayMetrics;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.AiProviderException.Reason;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.ModelProvider;
import br.com.doistecht.iaservice.provider.StreamChunk;
import br.com.doistecht.iaservice.structured.JsonSchemaValidator;
import br.com.doistecht.iaservice.usage.UsageRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

/** Troca entre provedores quando o escolhido está fora do ar. */
class GatewayProviderFallbackTest {

	private final ModelProvider gemini = mock(ModelProvider.class);

	private final ModelProvider ollama = mock(ModelProvider.class);

	private final ResponseCache cache = mock(ResponseCache.class);

	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

	private GatewayAiProvider gateway;

	private final ChatCommand command = new ChatCommand(null, "Oi");

	@BeforeEach
	void setUp() {
		given(gemini.name()).willReturn("gemini");
		given(ollama.name()).willReturn("ollama");
		given(cache.key(any(), anyString(), anyString(), any(), any())).willReturn("chave");
		given(cache.get("chave")).willReturn(Optional.empty());

		IaServiceProperties defaults = TestProperties.defaults();
		IaServiceProperties properties = new IaServiceProperties(defaults.adminKey(), defaults.auth(), defaults.cache(),
				defaults.pricing(), defaults.rag(), new IaServiceProperties.Providers("gemini", "ollama"));
		gateway = new GatewayAiProvider(new ProviderRouter(List.of(gemini, ollama), properties), cache,
				mock(UsageRecorder.class), new JsonSchemaValidator(), JsonMapper.builder().build(),
				new GatewayMetrics(meterRegistry));
	}

	@Test
	void shouldFallBackToSecondProviderWhenDefaultIsUnavailable() {
		given(gemini.chat(command)).willThrow(unavailable());
		given(ollama.chat(command)).willReturn(new ChatResult("Olá do Ollama", "llama3.2:3b", "ollama"));

		ChatResult result = gateway.chat(command);

		assertThat(result.provider()).isEqualTo("ollama");
		assertThat(result.fallback()).isTrue();
		verify(cache, never()).put(anyString(), any());
		assertThat(meterRegistry.get(GatewayMetrics.CALLS).tag("provider", "ollama").tag("fallback", "true")
				.counter().count()).isEqualTo(1.0);
		assertThat(meterRegistry.get(GatewayMetrics.CALLS).tag("provider", "gemini").tag("outcome", "failure")
				.counter().count()).isEqualTo(1.0);
	}

	@Test
	void shouldNotFallBackOnPermanentError() {
		given(gemini.chat(command)).willThrow(new AiProviderException("gemini", "requisição inválida", null));

		assertThatThrownBy(() -> gateway.chat(command)).isInstanceOf(AiProviderException.class);

		verify(ollama, never()).chat(any());
	}

	@Test
	void shouldNotFallBackWhenProviderWasRequestedExplicitly() {
		ChatCommand explicit = command.withProvider("gemini");
		given(gemini.chat(explicit)).willThrow(unavailable());

		assertThatThrownBy(() -> gateway.chat(explicit)).isInstanceOf(AiProviderException.class);

		verify(ollama, never()).chat(any());
	}

	@Test
	void shouldRouteToRequestedProvider() {
		ChatCommand toOllama = command.withProvider("ollama");
		given(ollama.chat(toOllama)).willReturn(new ChatResult("Olá", "llama3.2:3b", "ollama"));

		assertThat(gateway.chat(toOllama).provider()).isEqualTo("ollama");

		verify(gemini, never()).chat(any());
	}

	@Test
	void shouldReportFailureOfBothProviders() {
		given(gemini.chat(command)).willThrow(unavailable());
		given(ollama.chat(command)).willThrow(new AiProviderException("ollama", Reason.UNAVAILABLE, "fora", null, null));

		assertThatThrownBy(() -> gateway.chat(command))
				.isInstanceOfSatisfying(AiProviderException.class, ex -> assertThat(ex.getProvider()).isEqualTo("ollama"));
	}

	@Test
	void shouldStreamFromFallbackProviderWhenDefaultFailsBeforeFirstChunk() {
		given(gemini.chatStream(command)).willReturn(Flux.error(unavailable()));
		given(ollama.chatStream(command)).willReturn(Flux.just(new StreamChunk("Olá", "llama3.2:3b", null)));

		assertThat(gateway.chatStream(command).collectList().block())
				.extracting(StreamChunk::content)
				.containsExactly("Olá");
		assertThat(meterRegistry.get(GatewayMetrics.CALLS).tag("provider", "ollama").tag("operation", "stream")
				.tag("fallback", "true").counter().count()).isEqualTo(1.0);
	}

	@Test
	void shouldNotSwitchProviderAfterFirstStreamChunk() {
		given(gemini.chatStream(command)).willReturn(
				Flux.concat(Flux.just(new StreamChunk("Parte")), Flux.error(unavailable())));

		assertThatThrownBy(() -> gateway.chatStream(command).collectList().block())
				.isInstanceOf(AiProviderException.class);
		verify(ollama, never()).chatStream(any());
	}

	private static AiProviderException unavailable() {
		return new AiProviderException("gemini", Reason.UNAVAILABLE, "fora do ar", null, null);
	}

}

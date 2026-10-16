package br.com.doistecht.iaservice.provider.maritaca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatMessage;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.EmbeddingPurpose;
import br.com.doistecht.iaservice.provider.StreamChunk;
import br.com.doistecht.iaservice.provider.TokenUsage;
import br.com.doistecht.iaservice.provider.UnsupportedCapabilityException;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Testa o provedor contra um servidor local que imita a API da Maritaca. */
class MaritacaProviderTest {

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	private HttpServer server;

	private final AtomicReference<String> lastBody = new AtomicReference<>();

	private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

	private volatile int status = 200;

	private volatile String contentType = "application/json";

	private volatile String response = "";

	private MaritacaProvider provider;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/chat/completions", exchange -> {
			lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", contentType);
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		var properties = new MaritacaProperties(true, "chave-teste",
				"http://127.0.0.1:" + server.getAddress().getPort() + "/api", "sabiazinho-4", null,
				Duration.ofSeconds(2), Duration.ofSeconds(5));
		provider = new MaritacaProvider(properties, objectMapper, CircuitBreakerRegistry.ofDefaults(),
				RetryRegistry.ofDefaults());
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void shouldSendOpenAiCompatibleRequestAndReadAnswer() {
		response = """
				{"id":"1","model":"sabiazinho-4","choices":[{"index":0,"message":{"role":"assistant","content":"Olá!"},
				"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":3,"total_tokens":15}}
				""";

		ChatResult result = provider.chat(new ChatCommand("Seja breve.",
				List.of(new ChatMessage(ChatMessage.Role.USER, "Oi"), new ChatMessage(ChatMessage.Role.ASSISTANT, "Oi!")),
				"Tudo bem?"));

		assertThat(result.content()).isEqualTo("Olá!");
		assertThat(result.model()).isEqualTo("sabiazinho-4");
		assertThat(result.provider()).isEqualTo("maritaca");
		assertThat(result.usage()).isEqualTo(new TokenUsage(12, 3));
		assertThat(lastAuthorization.get()).isEqualTo("Bearer chave-teste");

		JsonNode body = objectMapper.readTree(lastBody.get());
		assertThat(body.path("model").asString()).isEqualTo("sabiazinho-4");
		assertThat(body.path("stream").asBoolean()).isFalse();
		assertThat(body.path("messages")).hasSize(4);
		assertThat(body.path("messages").get(0).path("role").asString()).isEqualTo("system");
		assertThat(body.path("messages").get(2).path("role").asString()).isEqualTo("assistant");
		assertThat(body.path("messages").get(3).path("content").asString()).isEqualTo("Tudo bem?");
	}

	@Test
	void shouldSendJsonSchemaInStructuredRequests() {
		response = """
				{"model":"sabiazinho-4","choices":[{"message":{"content":"{\\"nome\\":\\"Ana\\"}"}}]}
				""";

		ChatResult result = provider.structured(new ChatCommand(null, "Ana, 30 anos"),
				"{\"type\":\"object\",\"properties\":{\"nome\":{\"type\":\"string\"}}}");

		assertThat(result.content()).isEqualTo("{\"nome\":\"Ana\"}");
		JsonNode format = objectMapper.readTree(lastBody.get()).path("response_format");
		assertThat(format.path("type").asString()).isEqualTo("json_schema");
		assertThat(format.path("json_schema").path("schema").path("type").asString()).isEqualTo("object");
	}

	@Test
	void shouldStreamChunksUntilDoneAndKeepUsage() {
		contentType = "text/event-stream";
		response = """
				data: {"model":"sabiazinho-4","choices":[{"delta":{"role":"assistant","content":"Bom "}}]}

				data: {"model":"sabiazinho-4","choices":[{"delta":{"content":"dia"}}]}

				data: {"model":"sabiazinho-4","choices":[],"usage":{"prompt_tokens":5,"completion_tokens":2}}

				data: [DONE]

				""";

		List<StreamChunk> chunks = provider.chatStream(new ChatCommand(null, "Bom dia?")).collectList().block();

		assertThat(chunks).extracting(StreamChunk::content).containsExactly("Bom ", "dia", "");
		assertThat(chunks.getLast().usage()).isEqualTo(new TokenUsage(5, 2));
		JsonNode body = objectMapper.readTree(lastBody.get());
		assertThat(body.path("stream").asBoolean()).isTrue();
		assertThat(body.path("stream_options").path("include_usage").asBoolean()).isTrue();
	}

	@Test
	void shouldNotRetryRejectedKey() {
		status = 401;
		response = "{\"detail\":\"Invalid API key\"}";

		assertThatThrownBy(() -> provider.chat(new ChatCommand(null, "Oi")))
				.isInstanceOf(AiProviderException.class)
				.extracting(ex -> ((AiProviderException) ex).getReason())
				.isEqualTo(AiProviderException.Reason.FAILED);
	}

	@Test
	void shouldRejectEmbeddings() {
		assertThatThrownBy(() -> provider.embed(List.of("texto"), EmbeddingPurpose.DOCUMENT))
				.isInstanceOf(UnsupportedCapabilityException.class);
	}

	@Test
	void shouldRequireApiKeyWhenEnabled() {
		var properties = new MaritacaProperties(true, " ", "http://127.0.0.1/api", "sabiazinho-4", null,
				Duration.ofSeconds(1), Duration.ofSeconds(1));

		assertThatThrownBy(() -> new MaritacaProvider(properties, objectMapper, CircuitBreakerRegistry.ofDefaults(),
				RetryRegistry.ofDefaults()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("MARITACA_API_KEY");
	}

}

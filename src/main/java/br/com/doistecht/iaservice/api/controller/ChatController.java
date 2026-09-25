package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.ChatChunk;
import br.com.doistecht.iaservice.api.dto.ChatRequest;
import br.com.doistecht.iaservice.api.dto.ChatResponse;
import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.AiProviderException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Tag(name = "Chat", description = "Conversa com modelos de IA")
@RestController
@RequestMapping("/v1/chat")
public class ChatController {

	private final AiProvider aiProvider;

	public ChatController(AiProvider aiProvider) {
		this.aiProvider = aiProvider;
	}

	@Operation(summary = "Envia uma mensagem e recebe a resposta do modelo")
	@PostMapping
	public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
		return ChatResponse.from(aiProvider.chat(request.toCommand()));
	}

	@Operation(summary = "Envia uma mensagem e recebe a resposta em partes (Server-Sent Events)",
			description = """
					Cada evento `message` traz um JSON `{"content": "..."}` com o próximo trecho do texto.
					O evento `done` indica o fim da resposta e traz o modelo que respondeu (`{"model": "..."}`);
					o evento `error` indica falha durante a geração.""")
	@PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<Object>> stream(@Valid @RequestBody ChatRequest request) {
		AtomicReference<String> model = new AtomicReference<>();
		return aiProvider.chatStream(request.toCommand())
				.doOnNext(chunk -> {
					if (chunk.model() != null) {
						model.set(chunk.model());
					}
				})
				// O último pedaço costuma trazer só metadados (tokens), sem texto
				.filter(chunk -> chunk.content() != null && !chunk.content().isEmpty())
				.map(chunk -> ServerSentEvent.<Object>builder(new ChatChunk(chunk.content())).event("message").build())
				.concatWith(Mono.fromSupplier(() -> ServerSentEvent.<Object>builder(
						model.get() == null ? Map.of() : Map.of("model", model.get())).event("done").build()))
				// O status HTTP já foi enviado quando o stream começa, então a falha vira um evento
				.onErrorResume(AiProviderException.class, ex -> Mono.just(ServerSentEvent.<Object>builder(
						Map.of("detail", "Não foi possível obter resposta do provedor de IA.",
								"provider", ex.getProvider()))
						.event("error")
						.build()));
	}

}

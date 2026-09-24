package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.ChatRequest;
import br.com.doistecht.iaservice.api.dto.ChatResponse;
import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.ChatCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
		var result = aiProvider.chat(new ChatCommand(request.systemPrompt(), request.message()));
		return ChatResponse.from(result);
	}

}

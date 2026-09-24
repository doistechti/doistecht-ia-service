package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.EmbeddingRequest;
import br.com.doistecht.iaservice.api.dto.EmbeddingResponse;
import br.com.doistecht.iaservice.provider.AiProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Embeddings", description = "Vetores para busca semântica")
@RestController
@RequestMapping("/v1/embeddings")
public class EmbeddingController {

	private final AiProvider aiProvider;

	public EmbeddingController(AiProvider aiProvider) {
		this.aiProvider = aiProvider;
	}

	@Operation(summary = "Gera um embedding para cada texto")
	@PostMapping
	public EmbeddingResponse embed(@Valid @RequestBody EmbeddingRequest request) {
		return EmbeddingResponse.from(aiProvider.embed(request.texts(), request.purposeOrDefault()));
	}

}

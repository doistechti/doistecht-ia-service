package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.RagRequest;
import br.com.doistecht.iaservice.client.AuthenticatedClient;
import br.com.doistecht.iaservice.rag.RagAnswer;
import br.com.doistecht.iaservice.rag.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "RAG", description = "Perguntas respondidas com base nos documentos do cliente")
@RestController
@RequestMapping("/v1/rag")
public class RagController {

	private final RagService ragService;

	public RagController(RagService ragService) {
		this.ragService = ragService;
	}

	@Operation(summary = "Responde uma pergunta usando os documentos do cliente como fonte",
			description = "A busca considera apenas documentos do próprio cliente com status READY.")
	@PostMapping("/ask")
	public RagAnswer ask(@Valid @RequestBody RagRequest request,
			@Parameter(hidden = true) @RequestAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE) AuthenticatedClient client) {
		return ragService.ask(client.id(), request.question(), request.topK(), request.documentIds(),
				request.provider());
	}

}

package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.ClientCreateRequest;
import br.com.doistecht.iaservice.api.dto.ClientKeyResponse;
import br.com.doistecht.iaservice.api.dto.ClientResponse;
import br.com.doistecht.iaservice.api.dto.ClientUpdateRequest;
import br.com.doistecht.iaservice.client.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Admin - Clientes", description = "Cadastro dos projetos que consomem o gateway")
@RestController
@RequestMapping("/v1/admin/clients")
public class ClientAdminController {

	private final ClientService clientService;

	public ClientAdminController(ClientService clientService) {
		this.clientService = clientService;
	}

	@Operation(summary = "Cadastra um cliente e gera sua API key",
			description = "A chave completa só é exibida nesta resposta.")
	@PostMapping
	public ResponseEntity<ClientKeyResponse> create(@Valid @RequestBody ClientCreateRequest request) {
		var created = clientService.create(request.name(), request.rateLimitPerMinuteOrDefault(),
				request.dailyQuotaOrDefault(), request.defaultProvider());
		var location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.client().getId())
				.toUri();
		return ResponseEntity.created(location).body(ClientKeyResponse.from(created));
	}

	@Operation(summary = "Lista os clientes")
	@GetMapping
	public List<ClientResponse> list() {
		return clientService.list().stream().map(ClientResponse::from).toList();
	}

	@Operation(summary = "Detalha um cliente")
	@GetMapping("/{id}")
	public ClientResponse get(@PathVariable Long id) {
		return ClientResponse.from(clientService.get(id));
	}

	@Operation(summary = "Altera limites ou ativa/desativa o cliente")
	@PatchMapping("/{id}")
	public ClientResponse update(@PathVariable Long id, @Valid @RequestBody ClientUpdateRequest request) {
		return ClientResponse.from(clientService.update(id, request.toChanges()));
	}

	@Operation(summary = "Gera uma nova API key; a anterior deixa de funcionar",
			description = "A chave completa só é exibida nesta resposta.")
	@PostMapping("/{id}/rotate-key")
	public ClientKeyResponse rotateKey(@PathVariable Long id) {
		return ClientKeyResponse.from(clientService.rotateKey(id));
	}

}

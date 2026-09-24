package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.TemplateRequest;
import br.com.doistecht.iaservice.api.dto.TemplateResponse;
import br.com.doistecht.iaservice.api.dto.TemplateStatusRequest;
import br.com.doistecht.iaservice.template.PromptTemplate;
import br.com.doistecht.iaservice.template.PromptTemplateService;
import br.com.doistecht.iaservice.template.TemplateRenderer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Gestão dos templates de prompt. Na fase 3 estas rotas passam a exigir chave de administrador.
 */
@Tag(name = "Admin - Templates", description = "Gestão dos templates de prompt")
@RestController
@RequestMapping("/v1/admin/templates")
public class TemplateAdminController {

	private final PromptTemplateService templateService;

	private final TemplateRenderer renderer;

	private final ObjectMapper objectMapper;

	public TemplateAdminController(PromptTemplateService templateService, TemplateRenderer renderer,
			ObjectMapper objectMapper) {
		this.templateService = templateService;
		this.renderer = renderer;
		this.objectMapper = objectMapper;
	}

	@Operation(summary = "Lista todos os templates e versões")
	@GetMapping
	public List<TemplateResponse> list() {
		return templateService.listAll().stream().map(this::toResponse).toList();
	}

	@Operation(summary = "Lista as versões de um template, da mais recente para a mais antiga")
	@GetMapping("/{name}")
	public List<TemplateResponse> versions(@PathVariable String name) {
		return templateService.listVersions(name).stream().map(this::toResponse).toList();
	}

	@Operation(summary = "Cria um template ou uma nova versão de um template existente")
	@PostMapping
	public ResponseEntity<TemplateResponse> create(@Valid @RequestBody TemplateRequest request) {
		PromptTemplate created = templateService.create(request.toCommand());
		var location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{name}")
				.buildAndExpand(created.getName())
				.toUri();
		return ResponseEntity.created(location).body(toResponse(created));
	}

	@Operation(summary = "Ativa ou desativa uma versão do template")
	@PatchMapping("/{name}/versions/{version}")
	public TemplateResponse setStatus(@PathVariable String name, @PathVariable int version,
			@Valid @RequestBody TemplateStatusRequest request) {
		return toResponse(templateService.setActive(name, version, request.active()));
	}

	private TemplateResponse toResponse(PromptTemplate template) {
		var variables = new LinkedHashSet<>(renderer.variablesOf(template.getSystemPrompt()));
		variables.addAll(renderer.variablesOf(template.getUserPromptTemplate()));
		var outputSchema = template.getOutputSchema() == null ? null : objectMapper.readTree(template.getOutputSchema());
		return TemplateResponse.from(template, new ArrayList<>(variables), outputSchema);
	}

}

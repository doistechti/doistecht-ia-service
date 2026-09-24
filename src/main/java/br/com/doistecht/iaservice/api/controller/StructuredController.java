package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.StructuredRequest;
import br.com.doistecht.iaservice.api.dto.StructuredResponse;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.structured.StructuredOutputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Structured", description = "Respostas em JSON seguindo um schema")
@RestController
@RequestMapping("/v1/structured")
public class StructuredController {

	private static final String DEFAULT_SYSTEM_PROMPT =
			"Extraia ou gere as informações pedidas a partir do texto do usuário. "
					+ "Não invente dados que não estejam no texto.";

	private final StructuredOutputService structuredOutputService;

	public StructuredController(StructuredOutputService structuredOutputService) {
		this.structuredOutputService = structuredOutputService;
	}

	@Operation(summary = "Gera uma resposta em JSON validada contra o schema informado")
	@PostMapping
	public StructuredResponse generate(@Valid @RequestBody StructuredRequest request) {
		String systemPrompt = StringUtils.hasText(request.systemPrompt())
				? request.systemPrompt()
				: DEFAULT_SYSTEM_PROMPT;
		var result = structuredOutputService.generate(new ChatCommand(systemPrompt, request.input()),
				request.schema());
		return StructuredResponse.from(result);
	}

}

package br.com.doistecht.iaservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(

		@Schema(description = "Mensagem do usuário", example = "Explique o que é um AI Gateway em uma frase.")
		@NotBlank
		@Size(max = 10_000)
		String message,

		@Schema(description = "Instrução de comportamento do modelo (opcional)", example = "Responda de forma objetiva.")
		@Size(max = 4_000)
		String systemPrompt) {
}

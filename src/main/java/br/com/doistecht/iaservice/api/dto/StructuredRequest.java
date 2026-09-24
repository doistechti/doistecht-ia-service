package br.com.doistecht.iaservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record StructuredRequest(

		@Schema(description = "Texto de entrada", example = "João Silva, 32 anos, mora em Curitiba.")
		@NotBlank
		@Size(max = 20_000)
		String input,

		@Schema(description = "Instrução do que extrair ou gerar (opcional)",
				example = "Extraia os dados pessoais do texto.")
		@Size(max = 4_000)
		String systemPrompt,

		@Schema(description = "JSON Schema que a resposta deve seguir", example = """
				{"type": "object", "properties": {"nome": {"type": "string"}, "idade": {"type": "integer"},
				"cidade": {"type": "string"}}, "required": ["nome", "idade", "cidade"]}""")
		@NotNull
		JsonNode schema) {
}

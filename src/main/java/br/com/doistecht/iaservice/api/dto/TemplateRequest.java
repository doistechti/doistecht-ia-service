package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.template.NewPromptTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record TemplateRequest(

		@Schema(description = "Nome do template em minúsculas, separado por hífens. "
				+ "Se o nome já existir, é criada uma nova versão.", example = "traduzir-texto")
		@NotBlank
		@Size(max = 100)
		@Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "deve conter apenas letras minúsculas, números e hífens")
		String name,

		@Schema(description = "Instrução de comportamento do modelo; aceita variáveis {nome} (opcional)",
				example = "Você é um tradutor profissional.")
		@Size(max = 4_000)
		String systemPrompt,

		@Schema(description = "Texto enviado ao modelo, com variáveis no formato {nome}",
				example = "Traduza o texto abaixo para {idioma}:\n\n{texto}")
		@NotBlank
		@Size(max = 20_000)
		String userPromptTemplate,

		@Schema(description = "JSON Schema da resposta; se informado, a tarefa retorna JSON estruturado (opcional)")
		JsonNode outputSchema) {

	public NewPromptTemplate toCommand() {
		return new NewPromptTemplate(name, systemPrompt, userPromptTemplate, outputSchema);
	}

}

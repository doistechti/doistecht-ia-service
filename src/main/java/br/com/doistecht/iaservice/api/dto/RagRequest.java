package br.com.doistecht.iaservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RagRequest(

		@Schema(description = "Pergunta sobre os documentos", example = "Qual o prazo de reembolso?")
		@NotBlank
		@Size(max = 2_000)
		String question,

		@Schema(description = "Quantidade de trechos usados como contexto (padrão 4, máximo 20)", example = "4")
		@Positive
		@Max(20)
		Integer topK,

		@Schema(description = "Restringe a busca a estes documentos (opcional)", example = "[12]")
		@Size(max = 50)
		List<Long> documentIds,

		@Schema(description = "Provedor de IA que gera a resposta (opcional). A busca nos documentos sempre usa o "
				+ "provedor de embeddings com que eles foram indexados.", example = "gemini")
		@Pattern(regexp = "[a-z0-9-]{1,30}", message = "nome de provedor inválido")
		String provider) {
}

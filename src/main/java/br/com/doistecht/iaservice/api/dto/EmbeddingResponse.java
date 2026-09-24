package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.provider.EmbeddingResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record EmbeddingResponse(

		@Schema(description = "Modelo de embedding utilizado", example = "gemini-embedding-001")
		String model,

		@Schema(description = "Dimensão de cada vetor", example = "768")
		int dimensions,

		@Schema(description = "Um vetor normalizado (norma 1) por texto, na mesma ordem da requisição")
		List<float[]> embeddings) {

	public static EmbeddingResponse from(EmbeddingResult result) {
		return new EmbeddingResponse(result.model(), result.dimensions(), result.vectors());
	}

}

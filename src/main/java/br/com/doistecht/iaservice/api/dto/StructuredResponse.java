package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.structured.StructuredResult;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

public record StructuredResponse(

		@Schema(description = "JSON gerado pelo modelo, validado contra o schema")
		JsonNode data,

		@Schema(description = "Modelo que gerou a resposta", example = "gemini-2.5-flash")
		String model,

		@Schema(description = "Provedor utilizado", example = "gemini")
		String provider,

		@Schema(description = "true quando o modelo principal falhou e a resposta veio do modelo reserva")
		boolean fallback) {

	public static StructuredResponse from(StructuredResult result) {
		return new StructuredResponse(result.data(), result.model(), result.provider(), result.fallback());
	}

}

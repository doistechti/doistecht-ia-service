package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.task.TaskResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskResponse(

		@Schema(description = "Template executado", example = "resumir-texto")
		String template,

		@Schema(description = "Versão do template executada", example = "1")
		int version,

		@Schema(description = "Resposta em texto (templates sem schema de saída)")
		String content,

		@Schema(description = "Resposta em JSON (templates com schema de saída)")
		JsonNode data,

		@Schema(description = "Modelo que gerou a resposta", example = "gemini-2.5-flash")
		String model,

		@Schema(description = "Provedor utilizado", example = "gemini")
		String provider,

		@Schema(description = "true quando o modelo principal falhou e a resposta veio do modelo reserva")
		boolean fallback) {

	public static TaskResponse from(TaskResult result) {
		return new TaskResponse(result.template(), result.version(), result.content(), result.data(),
				result.model(), result.provider(), result.fallback());
	}

}

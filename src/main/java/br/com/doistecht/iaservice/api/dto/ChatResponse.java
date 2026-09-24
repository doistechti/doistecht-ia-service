package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.provider.ChatResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatResponse(

		@Schema(description = "Texto gerado pelo modelo")
		String content,

		@Schema(description = "Modelo que gerou a resposta", example = "gemini-2.5-flash")
		String model,

		@Schema(description = "Provedor utilizado", example = "gemini")
		String provider) {

	public static ChatResponse from(ChatResult result) {
		return new ChatResponse(result.content(), result.model(), result.provider());
	}

}

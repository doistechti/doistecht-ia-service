package br.com.doistecht.iaservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TemplateStatusRequest(

		@Schema(description = "Se a versão pode ser executada", example = "false")
		@NotNull
		Boolean active) {
}

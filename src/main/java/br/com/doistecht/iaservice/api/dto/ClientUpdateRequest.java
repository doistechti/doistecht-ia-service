package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.client.ClientService.ClientChanges;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/** Campos omitidos permanecem como estão. */
public record ClientUpdateRequest(

		@Schema(description = "Requisições por minuto", example = "20")
		@Positive
		@Max(10_000)
		Integer rateLimitPerMinute,

		@Schema(description = "Requisições por dia", example = "500")
		@Positive
		@Max(1_000_000)
		Integer dailyQuota,

		@Schema(description = "false desativa o cliente: a chave deixa de funcionar", example = "false")
		Boolean active) {

	public ClientChanges toChanges() {
		return new ClientChanges(rateLimitPerMinute, dailyQuota, active);
	}

}

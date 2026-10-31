package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.client.ClientService.ClientWithKey;
import io.swagger.v3.oas.annotations.media.Schema;

public record ClientKeyResponse(

		ClientResponse client,

		@Schema(description = "API key completa. Depois desta resposta, só pode ser obtida de novo pelo admin quando apiKeyAvailable for true.")
		String apiKey) {

	public static ClientKeyResponse from(ClientWithKey created, boolean apiKeyAvailable) {
		return new ClientKeyResponse(ClientResponse.from(created.client(), apiKeyAvailable), created.apiKey().value());
	}

}

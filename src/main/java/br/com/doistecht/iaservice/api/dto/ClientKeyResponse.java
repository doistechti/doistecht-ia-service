package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.client.ClientService.ClientWithKey;
import io.swagger.v3.oas.annotations.media.Schema;

public record ClientKeyResponse(

		ClientResponse client,

		@Schema(description = "API key completa. É exibida apenas nesta resposta: guarde-a em local seguro.")
		String apiKey) {

	public static ClientKeyResponse from(ClientWithKey created) {
		return new ClientKeyResponse(ClientResponse.from(created.client()), created.apiKey().value());
	}

}

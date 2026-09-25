package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.client.Client;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ClientResponse(
		Long id,
		String name,
		@Schema(description = "Início da API key, para identificá-la sem expô-la", example = "dtia_Xk3p9Qa")
		String apiKeyPrefix,
		int rateLimitPerMinute,
		int dailyQuota,
		@Schema(description = "Provedor de IA padrão do cliente; nulo usa o padrão global")
		String defaultProvider,
		boolean active,
		Instant createdAt) {

	public static ClientResponse from(Client client) {
		return new ClientResponse(client.getId(), client.getName(), client.getApiKeyPrefix(),
				client.getRateLimitPerMinute(), client.getDailyQuota(), client.getDefaultProvider(), client.isActive(),
				client.getCreatedAt());
	}

}

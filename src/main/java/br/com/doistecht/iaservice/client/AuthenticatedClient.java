package br.com.doistecht.iaservice.client;

/**
 * Cliente autenticado na requisição atual. Fica disponível como atributo da
 * requisição para controllers, rate limit e registro de uso.
 */
public record AuthenticatedClient(Long id, String name, int rateLimitPerMinute, int dailyQuota) {

	public static final String REQUEST_ATTRIBUTE = "br.com.doistecht.iaservice.authenticatedClient";

	static AuthenticatedClient from(Client client) {
		return new AuthenticatedClient(client.getId(), client.getName(), client.getRateLimitPerMinute(),
				client.getDailyQuota());
	}

}

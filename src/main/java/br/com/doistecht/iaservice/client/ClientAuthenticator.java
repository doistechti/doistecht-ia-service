package br.com.doistecht.iaservice.client;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Valida API keys de clientes, com cache em memória para não consultar o banco
 * a cada requisição. Chaves inválidas também ficam em cache, protegendo o banco
 * de tentativas repetidas.
 * <p>
 * Alterações feitas nesta instância limpam o cache na hora; em outras instâncias
 * elas valem após o TTL ({@code ia-service.auth.key-cache-ttl}).
 */
@Component
public class ClientAuthenticator {

	private static final int MAX_CACHED_KEYS = 10_000;

	private final ClientRepository repository;

	private final Cache<String, Optional<AuthenticatedClient>> cache;

	public ClientAuthenticator(ClientRepository repository, IaServiceProperties properties) {
		this.repository = repository;
		this.cache = Caffeine.newBuilder()
				.expireAfterWrite(properties.auth().keyCacheTtl())
				.maximumSize(MAX_CACHED_KEYS)
				.build();
	}

	public Optional<AuthenticatedClient> authenticate(String apiKey) {
		if (apiKey == null || !apiKey.startsWith(ApiKey.PREFIX)) {
			return Optional.empty();
		}
		return cache.get(ApiKey.hash(apiKey), hash -> repository.findByApiKeyHash(hash)
				.filter(Client::isActive)
				.map(AuthenticatedClient::from));
	}

	public void invalidateAll() {
		cache.invalidateAll();
	}

}

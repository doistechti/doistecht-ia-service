package br.com.doistecht.iaservice.client;

import br.com.doistecht.iaservice.gateway.ProviderRouter;
import br.com.doistecht.iaservice.gateway.UnknownProviderException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClientService {

	private final ClientRepository repository;

	private final ClientAuthenticator authenticator;

	private final ProviderRouter providerRouter;

	private final ApiKeyCipher cipher;

	public ClientService(ClientRepository repository, ClientAuthenticator authenticator,
			ProviderRouter providerRouter, ApiKeyCipher cipher) {
		this.repository = repository;
		this.authenticator = authenticator;
		this.providerRouter = providerRouter;
		this.cipher = cipher;
	}

	/** Cliente recém-criado ou com chave rotacionada, junto da chave em texto puro. */
	public record ClientWithKey(Client client, ApiKey apiKey) {
	}

	/**
	 * Alterações parciais: campos nulos são mantidos. Em {@code defaultProvider}, texto vazio
	 * remove o provedor padrão do cliente (volta a usar o padrão global).
	 */
	public record ClientChanges(Integer rateLimitPerMinute, Integer dailyQuota, Boolean active,
			String defaultProvider) {

		public ClientChanges(Integer rateLimitPerMinute, Integer dailyQuota, Boolean active) {
			this(rateLimitPerMinute, dailyQuota, active, null);
		}

	}

	@Transactional
	public ClientWithKey create(String name, int rateLimitPerMinute, int dailyQuota) {
		return create(name, rateLimitPerMinute, dailyQuota, null);
	}

	@Transactional
	public ClientWithKey create(String name, int rateLimitPerMinute, int dailyQuota, String defaultProvider) {
		if (repository.existsByName(name)) {
			throw new ClientAlreadyExistsException(name);
		}
		requireAvailable(defaultProvider);
		ApiKey apiKey = ApiKey.generate();
		Client client = new Client(name, apiKey, rateLimitPerMinute, dailyQuota);
		client.replaceApiKey(apiKey, cipher.encrypt(apiKey).orElse(null));
		client.setDefaultProvider(defaultProvider);
		return new ClientWithKey(repository.save(client), apiKey);
	}

	public List<Client> list() {
		return repository.findAllByOrderByNameAsc();
	}

	public Client get(Long id) {
		return repository.findById(id).orElseThrow(() -> new ClientNotFoundException(id));
	}

	/** Gera uma nova chave; a anterior deixa de funcionar imediatamente. */
	@Transactional
	public ClientWithKey rotateKey(Long id) {
		Client client = get(id);
		ApiKey apiKey = ApiKey.generate();
		client.replaceApiKey(apiKey, cipher.encrypt(apiKey).orElse(null));
		repository.flush();
		authenticator.invalidateAll();
		return new ClientWithKey(client, apiKey);
	}

	/** Chave completa do cliente, para o portal exibir; só existe para chaves geradas com a criptografia ativa. */
	public String revealKey(Long id) {
		Client client = get(id);
		return cipher.decrypt(client.getApiKeyEncrypted(), client.getApiKeyHash())
				.orElseThrow(() -> new ApiKeyUnavailableException(id));
	}

	/** Se a chave do cliente pode ser exibida de novo. */
	public boolean isKeyAvailable(Client client) {
		return cipher.isEnabled() && client.getApiKeyEncrypted() != null;
	}

	@Transactional
	public Client update(Long id, ClientChanges changes) {
		Client client = get(id);
		if (changes.rateLimitPerMinute() != null) {
			client.setRateLimitPerMinute(changes.rateLimitPerMinute());
		}
		if (changes.dailyQuota() != null) {
			client.setDailyQuota(changes.dailyQuota());
		}
		if (changes.active() != null) {
			client.setActive(changes.active());
		}
		if (changes.defaultProvider() != null) {
			String provider = changes.defaultProvider().isBlank() ? null : changes.defaultProvider();
			requireAvailable(provider);
			client.setDefaultProvider(provider);
		}
		repository.flush();
		authenticator.invalidateAll();
		return client;
	}

	private void requireAvailable(String provider) {
		if (provider != null && !providerRouter.isAvailable(provider)) {
			throw new UnknownProviderException(provider,
					providerRouter.all().stream().map(p -> p.name()).toList());
		}
	}

}

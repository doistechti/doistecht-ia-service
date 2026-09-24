package br.com.doistecht.iaservice.client;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClientService {

	private final ClientRepository repository;

	private final ClientAuthenticator authenticator;

	public ClientService(ClientRepository repository, ClientAuthenticator authenticator) {
		this.repository = repository;
		this.authenticator = authenticator;
	}

	/** Cliente recém-criado ou com chave rotacionada, junto da chave em texto puro. */
	public record ClientWithKey(Client client, ApiKey apiKey) {
	}

	/** Alterações parciais: campos nulos são mantidos. */
	public record ClientChanges(Integer rateLimitPerMinute, Integer dailyQuota, Boolean active) {
	}

	@Transactional
	public ClientWithKey create(String name, int rateLimitPerMinute, int dailyQuota) {
		if (repository.existsByName(name)) {
			throw new ClientAlreadyExistsException(name);
		}
		ApiKey apiKey = ApiKey.generate();
		Client client = repository.save(new Client(name, apiKey, rateLimitPerMinute, dailyQuota));
		return new ClientWithKey(client, apiKey);
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
		client.replaceApiKey(apiKey);
		repository.flush();
		authenticator.invalidateAll();
		return new ClientWithKey(client, apiKey);
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
		repository.flush();
		authenticator.invalidateAll();
		return client;
	}

}

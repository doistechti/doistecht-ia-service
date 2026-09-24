package br.com.doistecht.iaservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.doistecht.iaservice.config.TestProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClientAuthenticatorTest {

	private final ClientRepository repository = mock(ClientRepository.class);

	private final ClientAuthenticator authenticator = new ClientAuthenticator(repository, TestProperties.defaults());

	@Test
	void shouldAuthenticateActiveClientAndCacheResult() {
		ApiKey key = ApiKey.generate();
		given(repository.findByApiKeyHash(key.hash())).willReturn(Optional.of(new Client("portal", key, 10, 200)));

		assertThat(authenticator.authenticate(key.value())).hasValueSatisfying(c -> assertThat(c.name()).isEqualTo("portal"));
		assertThat(authenticator.authenticate(key.value())).isPresent();
		verify(repository, times(1)).findByApiKeyHash(key.hash());
	}

	@Test
	void shouldRejectInactiveClient() {
		ApiKey key = ApiKey.generate();
		Client client = new Client("portal", key, 10, 200);
		client.setActive(false);
		given(repository.findByApiKeyHash(key.hash())).willReturn(Optional.of(client));

		assertThat(authenticator.authenticate(key.value())).isEmpty();
	}

	@Test
	void shouldNotQueryDatabaseForKeysWithoutPrefix() {
		assertThat(authenticator.authenticate("chave-qualquer")).isEmpty();
		assertThat(authenticator.authenticate(null)).isEmpty();
		verify(repository, never()).findByApiKeyHash(anyString());
	}

	@Test
	void shouldReloadAfterInvalidation() {
		ApiKey key = ApiKey.generate();
		given(repository.findByApiKeyHash(key.hash())).willReturn(Optional.empty());

		authenticator.authenticate(key.value());
		authenticator.invalidateAll();
		authenticator.authenticate(key.value());

		verify(repository, times(2)).findByApiKeyHash(key.hash());
	}

}

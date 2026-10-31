package br.com.doistecht.iaservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApiKeyCipherTest {

	private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	private static final String OTHER_KEY = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

	private final ApiKeyCipher cipher = new ApiKeyCipher(new ApiKeyEncryptionProperties(KEY));

	@Test
	void shouldEncryptAndDecryptKey() {
		ApiKey key = ApiKey.generate();

		String stored = cipher.encrypt(key).orElseThrow();

		assertThat(stored).startsWith("v1:").doesNotContain(key.value());
		assertThat(cipher.decrypt(stored, key.hash())).contains(key.value());
	}

	@Test
	void shouldUseRandomIvForEachEncryption() {
		ApiKey key = ApiKey.generate();

		assertThat(cipher.encrypt(key)).isNotEqualTo(cipher.encrypt(key));
	}

	@Test
	void shouldNotDecryptWithAnotherClientHash() {
		String stored = cipher.encrypt(ApiKey.generate()).orElseThrow();

		assertThat(cipher.decrypt(stored, ApiKey.generate().hash())).isEmpty();
	}

	@Test
	void shouldNotDecryptWithAnotherEncryptionKey() {
		ApiKey key = ApiKey.generate();
		String stored = cipher.encrypt(key).orElseThrow();

		ApiKeyCipher other = new ApiKeyCipher(new ApiKeyEncryptionProperties(OTHER_KEY));

		assertThat(other.decrypt(stored, key.hash())).isEmpty();
	}

	@Test
	void shouldDoNothingWhenDisabled() {
		ApiKeyCipher disabled = new ApiKeyCipher(new ApiKeyEncryptionProperties(""));
		ApiKey key = ApiKey.generate();

		assertThat(disabled.isEnabled()).isFalse();
		assertThat(disabled.encrypt(key)).isEmpty();
		assertThat(disabled.decrypt(cipher.encrypt(key).orElseThrow(), key.hash())).isEmpty();
	}

	@Test
	void shouldIgnoreMissingOrMalformedValue() {
		String hash = ApiKey.generate().hash();

		assertThat(cipher.decrypt(null, hash)).isEmpty();
		assertThat(cipher.decrypt("v1:!!!", hash)).isEmpty();
		assertThat(cipher.decrypt("v1:AAAA", hash)).isEmpty();
	}

	@Test
	void shouldRejectKeyWithWrongSize() {
		assertThatThrownBy(() -> new ApiKeyCipher(new ApiKeyEncryptionProperties("c2hvcnQ=")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32 bytes");
	}

}

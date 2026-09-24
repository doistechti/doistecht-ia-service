package br.com.doistecht.iaservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyTest {

	@Test
	void shouldGenerateKeyWithPrefixAndMatchingHash() {
		ApiKey key = ApiKey.generate();

		assertThat(key.value()).startsWith("dtia_").hasSize(5 + 43);
		assertThat(key.prefix()).isEqualTo(key.value().substring(0, 12));
		assertThat(key.hash()).hasSize(64).isEqualTo(ApiKey.hash(key.value()));
	}

	@Test
	void shouldGenerateDifferentKeys() {
		assertThat(ApiKey.generate().value()).isNotEqualTo(ApiKey.generate().value());
	}

	@Test
	void shouldNotExposeFullKeyInToString() {
		ApiKey key = ApiKey.generate();

		assertThat(key.toString()).contains(key.prefix()).doesNotContain(key.value());
	}

}

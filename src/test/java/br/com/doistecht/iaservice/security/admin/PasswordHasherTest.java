package br.com.doistecht.iaservice.security.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

	private static final String INITIAL_HASH = "pbkdf2-sha256$210000$78LoEpTNejdzoono7GrTzg==$"
			+ "CMbIsOS2+5rJXLOsFNiGLkOmh9AaUyjku/BERDZRrz0=";

	private final PasswordHasher hasher = new PasswordHasher();

	@Test
	void shouldVerifySeededInitialPassword() {
		assertThat(hasher.matches("brenno123456", INITIAL_HASH)).isTrue();
		assertThat(hasher.matches("senha-incorreta", INITIAL_HASH)).isFalse();
	}

	@Test
	void shouldUseRandomSaltAndRejectMalformedHashes() {
		String first = hasher.hash("uma-senha-segura");
		String second = hasher.hash("uma-senha-segura");

		assertThat(first).isNotEqualTo(second);
		assertThat(hasher.matches("uma-senha-segura", first)).isTrue();
		assertThat(hasher.matches("uma-senha-segura", "hash-invalido")).isFalse();
	}
}

package br.com.doistecht.iaservice.client;

import br.com.doistecht.iaservice.support.Hashes;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API key de cliente no formato {@code dtia_<43 caracteres aleatórios>}.
 * <p>
 * A chave tem 256 bits de entropia, então um SHA-256 simples é suficiente para
 * guardá-la: não há como adivinhá-la por força bruta, ao contrário de senhas
 * escolhidas por pessoas (que exigiriam bcrypt ou Argon2).
 *
 * @param value  chave completa, exibida ao cliente uma única vez
 * @param hash   SHA-256 da chave em hexadecimal, gravado no banco
 * @param prefix início da chave, usado para identificá-la sem expô-la
 */
public record ApiKey(String value, String hash, String prefix) {

	public static final String PREFIX = "dtia_";

	private static final int RANDOM_BYTES = 32;

	private static final int DISPLAY_PREFIX_LENGTH = 12;

	private static final SecureRandom RANDOM = new SecureRandom();

	public static ApiKey generate() {
		byte[] bytes = new byte[RANDOM_BYTES];
		RANDOM.nextBytes(bytes);
		String value = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		return new ApiKey(value, hash(value), value.substring(0, DISPLAY_PREFIX_LENGTH));
	}

	public static String hash(String value) {
		return Hashes.sha256Hex(value);
	}

	// Evita que a chave completa apareça em logs por engano
	@Override
	public String toString() {
		return "ApiKey[prefix=" + prefix + "]";
	}

}

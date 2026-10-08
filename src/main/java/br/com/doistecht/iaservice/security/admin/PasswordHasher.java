package br.com.doistecht.iaservice.security.admin;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

/** Hash de senhas com PBKDF2-HMAC-SHA256, salt aleatório e comparação em tempo constante. */
@Component
public class PasswordHasher {

	private static final String PREFIX = "pbkdf2-sha256";
	private static final int ITERATIONS = 210_000;
	private static final int SALT_BYTES = 16;
	private static final int HASH_BYTES = 32;

	private final SecureRandom random = new SecureRandom();

	public String hash(String password) {
		byte[] salt = new byte[SALT_BYTES];
		random.nextBytes(salt);
		return encode(ITERATIONS, salt, derive(password, salt, ITERATIONS));
	}

	public boolean matches(String password, String encoded) {
		if (password == null || encoded == null) return false;
		try {
			String[] parts = encoded.split("\\$", -1);
			if (parts.length != 4 || !PREFIX.equals(parts[0])) return false;
			int iterations = Integer.parseInt(parts[1]);
			byte[] salt = Base64.getDecoder().decode(parts[2]);
			byte[] expected = Base64.getDecoder().decode(parts[3]);
			return MessageDigest.isEqual(expected, derive(password, salt, iterations));
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private static String encode(int iterations, byte[] salt, byte[] hash) {
		return PREFIX + "$" + iterations + "$" + Base64.getEncoder().encodeToString(salt) + "$"
				+ Base64.getEncoder().encodeToString(hash);
	}

	private static byte[] derive(String password, byte[] salt, int iterations) {
		PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BYTES * 8);
		try {
			return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
		}
		catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
			throw new IllegalStateException("PBKDF2-HMAC-SHA256 não está disponível", ex);
		}
		finally {
			spec.clearPassword();
		}
	}
}

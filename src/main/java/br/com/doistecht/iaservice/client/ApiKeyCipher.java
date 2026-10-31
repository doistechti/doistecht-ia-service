package br.com.doistecht.iaservice.client;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Criptografa as API keys com AES-256-GCM para o portal poder exibi-las de novo.
 * <p>
 * O hash da chave entra como dado associado (AAD): o texto cifrado só abre junto do hash
 * do mesmo cliente, então copiar a coluna de um cliente para outro não funciona.
 * Formato gravado: {@code v1:<Base64(iv || texto cifrado com tag)>}.
 */
@Component
public class ApiKeyCipher {

	private static final String VERSION = "v1:";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int KEY_BYTES = 32;
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKey key;

	private final SecureRandom random = new SecureRandom();

	public ApiKeyCipher(ApiKeyEncryptionProperties properties) {
		this.key = parseKey(properties.key());
	}

	private static SecretKey parseKey(String encoded) {
		if (encoded == null || encoded.isBlank()) return null;
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(encoded.strip());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("ia-service.api-key-encryption.key precisa estar em Base64", ex);
		}
		if (bytes.length != KEY_BYTES) {
			throw new IllegalStateException(
					"ia-service.api-key-encryption.key precisa ter 32 bytes (openssl rand -base64 32)");
		}
		return new SecretKeySpec(bytes, "AES");
	}

	public boolean isEnabled() {
		return key != null;
	}

	/** Chave cifrada para gravar no banco; vazio quando a criptografia está desativada. */
	public Optional<String> encrypt(ApiKey apiKey) {
		if (!isEnabled()) return Optional.empty();
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, apiKey.hash());
			byte[] encrypted = cipher.doFinal(apiKey.value().getBytes(StandardCharsets.UTF_8));
			byte[] payload = new byte[IV_BYTES + encrypted.length];
			System.arraycopy(iv, 0, payload, 0, IV_BYTES);
			System.arraycopy(encrypted, 0, payload, IV_BYTES, encrypted.length);
			return Optional.of(VERSION + Base64.getEncoder().encodeToString(payload));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Falha ao criptografar a API key", ex);
		}
	}

	/** Chave em texto puro; vazio quando não há o que abrir ou a chave de criptografia não confere. */
	public Optional<String> decrypt(String stored, String apiKeyHash) {
		if (!isEnabled() || stored == null || !stored.startsWith(VERSION)) return Optional.empty();
		try {
			byte[] payload = Base64.getDecoder().decode(stored.substring(VERSION.length()));
			if (payload.length <= IV_BYTES) return Optional.empty();
			byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
			Cipher cipher = cipher(Cipher.DECRYPT_MODE, iv, apiKeyHash);
			byte[] plain = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
			return Optional.of(new String(plain, StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	private Cipher cipher(int mode, byte[] iv, String apiKeyHash) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance(TRANSFORMATION);
		cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
		cipher.updateAAD(apiKeyHash.getBytes(StandardCharsets.UTF_8));
		return cipher;
	}

}

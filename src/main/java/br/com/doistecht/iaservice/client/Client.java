package br.com.doistecht.iaservice.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Projeto que consome o gateway. A autenticação usa o hash da API key; o prefixo identifica a chave
 * em logs e telas, e a versão criptografada (opcional) permite exibi-la de novo no portal.
 */
@Entity
@Table(name = "client")
public class Client {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 64)
	private String apiKeyHash;

	@Column(nullable = false, length = 16)
	private String apiKeyPrefix;

	/** API key criptografada pelo {@link ApiKeyCipher}; {@code null} quando não pode ser recuperada. */
	@Column(length = 255)
	private String apiKeyEncrypted;

	@Column(nullable = false)
	private int rateLimitPerMinute;

	@Column(nullable = false)
	private int dailyQuota;

	/** Provedor de IA padrão do cliente; {@code null} usa o padrão global. */
	@Column(length = 30)
	private String defaultProvider;

	@Column(nullable = false)
	private boolean active = true;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected Client() {
	}

	public Client(String name, ApiKey apiKey, int rateLimitPerMinute, int dailyQuota) {
		this.name = name;
		this.rateLimitPerMinute = rateLimitPerMinute;
		this.dailyQuota = dailyQuota;
		this.createdAt = Instant.now();
		replaceApiKey(apiKey);
	}

	public void replaceApiKey(ApiKey apiKey) {
		replaceApiKey(apiKey, null);
	}

	public void replaceApiKey(ApiKey apiKey, String encryptedKey) {
		this.apiKeyHash = apiKey.hash();
		this.apiKeyPrefix = apiKey.prefix();
		this.apiKeyEncrypted = encryptedKey;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getApiKeyHash() {
		return apiKeyHash;
	}

	public String getApiKeyPrefix() {
		return apiKeyPrefix;
	}

	public String getApiKeyEncrypted() {
		return apiKeyEncrypted;
	}

	public int getRateLimitPerMinute() {
		return rateLimitPerMinute;
	}

	public void setRateLimitPerMinute(int rateLimitPerMinute) {
		this.rateLimitPerMinute = rateLimitPerMinute;
	}

	public int getDailyQuota() {
		return dailyQuota;
	}

	public void setDailyQuota(int dailyQuota) {
		this.dailyQuota = dailyQuota;
	}

	public String getDefaultProvider() {
		return defaultProvider;
	}

	public void setDefaultProvider(String defaultProvider) {
		this.defaultProvider = defaultProvider;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

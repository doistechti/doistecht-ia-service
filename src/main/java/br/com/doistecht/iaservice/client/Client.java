package br.com.doistecht.iaservice.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Projeto que consome o gateway. A API key nunca é guardada: apenas o hash
 * (para autenticar) e o prefixo (para identificar a chave em logs e telas).
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

	@Column(nullable = false)
	private int rateLimitPerMinute;

	@Column(nullable = false)
	private int dailyQuota;

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
		this.apiKeyHash = apiKey.hash();
		this.apiKeyPrefix = apiKey.prefix();
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

package br.com.doistecht.iaservice.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro de uma chamada ao provedor de IA (ou de uma resposta servida do cache).
 */
@Entity
@Table(name = "usage_record")
public class UsageRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long clientId;

	@Column(nullable = false, length = 200)
	private String endpoint;

	@Column(nullable = false, length = 20)
	private String operation;

	@Column(nullable = false, length = 30)
	private String provider;

	@Column(length = 100)
	private String model;

	private Integer promptTokens;

	private Integer outputTokens;

	@Column(nullable = false)
	private int latencyMs;

	@Column(precision = 12, scale = 6)
	private BigDecimal estimatedCost;

	@Column(nullable = false)
	private boolean cacheHit;

	@Column(nullable = false)
	private boolean success;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected UsageRecord() {
	}

	public UsageRecord(UsageEvent event, BigDecimal estimatedCost) {
		this.clientId = event.clientId();
		this.endpoint = event.endpoint();
		this.operation = event.operation().value();
		this.provider = event.provider();
		this.model = event.model();
		this.promptTokens = event.usage() == null ? null : event.usage().promptTokens();
		this.outputTokens = event.usage() == null ? null : event.usage().outputTokens();
		this.latencyMs = (int) Math.min(Integer.MAX_VALUE, event.latencyMs());
		this.estimatedCost = estimatedCost;
		this.cacheHit = event.cacheHit();
		this.success = event.success();
		this.createdAt = event.occurredAt();
	}

	public Long getId() {
		return id;
	}

	public Long getClientId() {
		return clientId;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public String getOperation() {
		return operation;
	}

	public String getProvider() {
		return provider;
	}

	public String getModel() {
		return model;
	}

	public Integer getPromptTokens() {
		return promptTokens;
	}

	public Integer getOutputTokens() {
		return outputTokens;
	}

	public int getLatencyMs() {
		return latencyMs;
	}

	public BigDecimal getEstimatedCost() {
		return estimatedCost;
	}

	public boolean isCacheHit() {
		return cacheHit;
	}

	public boolean isSuccess() {
		return success;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

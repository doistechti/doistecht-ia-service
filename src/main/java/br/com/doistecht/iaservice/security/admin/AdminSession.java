package br.com.doistecht.iaservice.security.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_session")
public class AdminSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long adminUserId;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private Instant expiresAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected AdminSession() {
	}

	public AdminSession(Long adminUserId, String tokenHash, Instant expiresAt) {
		this.adminUserId = adminUserId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public Long getAdminUserId() { return adminUserId; }
	public String getTokenHash() { return tokenHash; }
	public Instant getExpiresAt() { return expiresAt; }
	public Instant getCreatedAt() { return createdAt; }
}

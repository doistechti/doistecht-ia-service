package br.com.doistecht.iaservice.security.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_user")
public class AdminUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(nullable = false, length = 255)
	private String passwordHash;

	@Column(nullable = false, length = 30)
	private String role = "ADMIN";

	@Column(nullable = false)
	private boolean mustChangePassword = true;

	@Column(nullable = false)
	private boolean active = true;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected AdminUser() {
	}

	public AdminUser(String email, String passwordHash, boolean mustChangePassword) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.mustChangePassword = mustChangePassword;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
		this.mustChangePassword = false;
		this.updatedAt = Instant.now();
	}

	public Long getId() { return id; }
	public String getEmail() { return email; }
	public String getPasswordHash() { return passwordHash; }
	public String getRole() { return role; }
	public boolean isMustChangePassword() { return mustChangePassword; }
	public boolean isActive() { return active; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}

package br.com.doistecht.iaservice.security.admin;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminSessionRepository extends JpaRepository<AdminSession, Long> {
	Optional<AdminSession> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
	void deleteByTokenHash(String tokenHash);
	void deleteByAdminUserId(Long adminUserId);
	void deleteByExpiresAtBefore(Instant now);
}

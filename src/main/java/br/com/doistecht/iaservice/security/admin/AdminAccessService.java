package br.com.doistecht.iaservice.security.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminAccessService {

	private static final String TOKEN_PREFIX = "dtia_admin_";
	private static final int TOKEN_BYTES = 32;

	private final AdminUserRepository users;
	private final AdminSessionRepository sessions;
	private final PasswordHasher passwordHasher;
	private final AdminAccessProperties properties;
	private final SecureRandom random = new SecureRandom();

	public record IssuedSession(String token, Instant expiresAt, AdminPrincipal admin) {
	}

	public AdminAccessService(AdminUserRepository users, AdminSessionRepository sessions,
			PasswordHasher passwordHasher, AdminAccessProperties properties) {
		this.users = users;
		this.sessions = sessions;
		this.passwordHasher = passwordHasher;
		this.properties = properties;
	}

	@Transactional
	public IssuedSession login(String email, String password) {
		AdminUser user = users.findByEmailIgnoreCase(normalizeEmail(email))
				.filter(AdminUser::isActive)
				.orElseThrow(AdminAuthenticationException::new);
		if (!passwordHasher.matches(password, user.getPasswordHash())) {
			throw new AdminAuthenticationException();
		}
		sessions.deleteByExpiresAtBefore(Instant.now());
		return issue(user);
	}

	public Optional<AdminPrincipal> authenticate(String token) {
		if (token == null || !token.startsWith(TOKEN_PREFIX)) return Optional.empty();
		return sessions.findByTokenHashAndExpiresAtAfter(hashToken(token), Instant.now())
				.flatMap(session -> users.findById(session.getAdminUserId()))
				.filter(AdminUser::isActive)
				.map(user -> principal(user, hashToken(token)));
	}

	@Transactional
	public IssuedSession changePassword(AdminPrincipal principal, String currentPassword, String newPassword) {
		AdminUser user = users.findById(principal.id()).filter(AdminUser::isActive)
				.orElseThrow(AdminAuthenticationException::new);
		if (!passwordHasher.matches(currentPassword, user.getPasswordHash())) {
			throw new InvalidPasswordException("A senha atual está incorreta.");
		}
		validateNewPassword(currentPassword, newPassword);
		user.changePassword(passwordHasher.hash(newPassword));
		sessions.deleteByAdminUserId(user.getId());
		users.flush();
		return issue(user);
	}

	@Transactional
	public void logout(AdminPrincipal principal) {
		sessions.deleteByTokenHash(principal.tokenHash());
	}

	private IssuedSession issue(AdminUser user) {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		String token = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant expiresAt = Instant.now().plus(properties.sessionTtl());
		String tokenHash = hashToken(token);
		sessions.save(new AdminSession(user.getId(), tokenHash, expiresAt));
		return new IssuedSession(token, expiresAt, principal(user, tokenHash));
	}

	private static void validateNewPassword(String currentPassword, String newPassword) {
		if (newPassword == null || newPassword.length() < 12 || newPassword.length() > 128) {
			throw new InvalidPasswordException("A nova senha deve ter entre 12 e 128 caracteres.");
		}
		if (newPassword.equals(currentPassword)) {
			throw new InvalidPasswordException("A nova senha deve ser diferente da senha atual.");
		}
	}

	private static String normalizeEmail(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

	private static AdminPrincipal principal(AdminUser user, String tokenHash) {
		return new AdminPrincipal(user.getId(), user.getEmail(), user.getRole(), user.isMustChangePassword(), tokenHash);
	}

	private static String hashToken(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 não está disponível", ex);
		}
	}
}

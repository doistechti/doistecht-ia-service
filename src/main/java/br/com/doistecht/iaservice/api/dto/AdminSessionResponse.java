package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.security.admin.AdminAccessService.IssuedSession;
import br.com.doistecht.iaservice.security.admin.AdminPrincipal;
import java.time.Instant;

public record AdminSessionResponse(String token, Instant expiresAt, AdminUserResponse user) {

	public static AdminSessionResponse from(IssuedSession session) {
		return new AdminSessionResponse(session.token(), session.expiresAt(), AdminUserResponse.from(session.admin()));
	}

	public record AdminUserResponse(Long id, String email, String role, boolean mustChangePassword) {
		public static AdminUserResponse from(AdminPrincipal principal) {
			return new AdminUserResponse(principal.id(), principal.email(), principal.role(),
					principal.mustChangePassword());
		}
	}
}

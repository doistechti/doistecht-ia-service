package br.com.doistecht.iaservice.security.admin;

public record AdminPrincipal(Long id, String email, String role, boolean mustChangePassword, String tokenHash) {
	public static final String REQUEST_ATTRIBUTE = "br.com.doistecht.iaservice.adminPrincipal";
}

package br.com.doistecht.iaservice.security.admin;

public class AdminAuthenticationException extends RuntimeException {
	public AdminAuthenticationException() {
		super("E-mail ou senha inválidos.");
	}
}

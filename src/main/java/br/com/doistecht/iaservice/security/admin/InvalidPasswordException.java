package br.com.doistecht.iaservice.security.admin;

public class InvalidPasswordException extends RuntimeException {
	public InvalidPasswordException(String message) {
		super(message);
	}
}

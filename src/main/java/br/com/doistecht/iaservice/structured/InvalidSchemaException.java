package br.com.doistecht.iaservice.structured;

/**
 * O JSON Schema enviado pelo cliente não é válido ou não é permitido.
 */
public class InvalidSchemaException extends RuntimeException {

	public InvalidSchemaException(String message) {
		super(message);
	}

}

package br.com.doistecht.iaservice.structured;

import java.util.List;

/**
 * O modelo não produziu um JSON válido para o schema, mesmo após nova tentativa.
 */
public class InvalidStructuredOutputException extends RuntimeException {

	private final List<String> errors;

	public InvalidStructuredOutputException(List<String> errors) {
		super("Resposta do modelo não atende ao schema: " + errors);
		this.errors = List.copyOf(errors);
	}

	public List<String> getErrors() {
		return errors;
	}

}

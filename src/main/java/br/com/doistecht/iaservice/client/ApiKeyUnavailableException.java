package br.com.doistecht.iaservice.client;

public class ApiKeyUnavailableException extends RuntimeException {

	public ApiKeyUnavailableException(Long id) {
		super("A API key do cliente %d não pode ser exibida: ela foi gerada antes de as chaves serem guardadas. Gere uma nova chave."
				.formatted(id));
	}

}

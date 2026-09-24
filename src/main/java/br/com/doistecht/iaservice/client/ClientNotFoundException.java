package br.com.doistecht.iaservice.client;

public class ClientNotFoundException extends RuntimeException {

	public ClientNotFoundException(Long id) {
		super("Cliente %d não encontrado.".formatted(id));
	}

}

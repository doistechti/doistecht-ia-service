package br.com.doistecht.iaservice.client;

public class ClientAlreadyExistsException extends RuntimeException {

	public ClientAlreadyExistsException(String name) {
		super("Já existe um cliente com o nome '%s'.".formatted(name));
	}

}

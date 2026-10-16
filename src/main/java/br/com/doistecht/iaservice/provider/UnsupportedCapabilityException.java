package br.com.doistecht.iaservice.provider;

/**
 * O provedor pedido não oferece a operação (ex.: embeddings na Maritaca). É um erro de
 * requisição (HTTP 400): repetir no mesmo provedor nunca vai funcionar.
 */
public class UnsupportedCapabilityException extends RuntimeException {

	public UnsupportedCapabilityException(String message) {
		super(message);
	}

}

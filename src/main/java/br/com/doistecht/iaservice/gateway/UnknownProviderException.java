package br.com.doistecht.iaservice.gateway;

import java.util.Collection;
import java.util.TreeSet;

/**
 * O provedor pedido não existe ou não está habilitado.
 */
public class UnknownProviderException extends RuntimeException {

	public UnknownProviderException(String provider, Collection<String> available) {
		super("Provedor '%s' não está disponível. Disponíveis: %s".formatted(provider,
				String.join(", ", new TreeSet<>(available))));
	}

}

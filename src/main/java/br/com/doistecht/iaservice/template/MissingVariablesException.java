package br.com.doistecht.iaservice.template;

import java.util.List;

/**
 * Variáveis exigidas pelo template não foram informadas.
 */
public class MissingVariablesException extends RuntimeException {

	private final List<String> missing;

	public MissingVariablesException(List<String> missing) {
		super("Variáveis ausentes: " + missing);
		this.missing = List.copyOf(missing);
	}

	public List<String> getMissing() {
		return missing;
	}

}

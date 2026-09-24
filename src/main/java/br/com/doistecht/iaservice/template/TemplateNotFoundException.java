package br.com.doistecht.iaservice.template;

/**
 * Template inexistente, ou sem versão ativa correspondente.
 */
public class TemplateNotFoundException extends RuntimeException {

	public TemplateNotFoundException(String name, Integer version) {
		super(version == null
				? "Template '%s' não encontrado ou sem versão ativa.".formatted(name)
				: "Template '%s' versão %d não encontrado ou inativo.".formatted(name, version));
	}

}

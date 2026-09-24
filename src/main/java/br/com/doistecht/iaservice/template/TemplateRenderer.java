package br.com.doistecht.iaservice.template;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Substitui variáveis no formato {@code {nome}} nos textos dos templates.
 * <p>
 * A substituição é feita em uma única passada: se o valor de uma variável
 * contiver chaves, elas não são interpretadas como novas variáveis.
 */
@Component
public class TemplateRenderer {

	private static final Pattern VARIABLE = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)}");

	/** Variáveis usadas no texto, na ordem em que aparecem. */
	public Set<String> variablesOf(String template) {
		Set<String> variables = new LinkedHashSet<>();
		if (template != null) {
			Matcher matcher = VARIABLE.matcher(template);
			while (matcher.find()) {
				variables.add(matcher.group(1));
			}
		}
		return variables;
	}

	/**
	 * Garante que todas as variáveis usadas nos textos foram informadas.
	 *
	 * @throws MissingVariablesException com a lista de variáveis ausentes
	 */
	public void requireVariables(Map<String, String> values, String... templates) {
		Set<String> missing = new LinkedHashSet<>();
		for (String template : templates) {
			for (String variable : variablesOf(template)) {
				if (values.get(variable) == null) {
					missing.add(variable);
				}
			}
		}
		if (!missing.isEmpty()) {
			throw new MissingVariablesException(List.copyOf(missing));
		}
	}

	public String render(String template, Map<String, String> values) {
		if (template == null) {
			return null;
		}
		requireVariables(values, template);
		return VARIABLE.matcher(template)
				.replaceAll(match -> Matcher.quoteReplacement(values.get(match.group(1))));
	}

}

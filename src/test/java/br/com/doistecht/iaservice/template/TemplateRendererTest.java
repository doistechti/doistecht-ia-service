package br.com.doistecht.iaservice.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

	private final TemplateRenderer renderer = new TemplateRenderer();

	@Test
	void shouldListVariablesInOrderWithoutDuplicates() {
		assertThat(renderer.variablesOf("Resuma {texto} em {linhas} linhas. {texto}"))
				.containsExactly("texto", "linhas");
	}

	@Test
	void shouldIgnoreBracesThatAreNotVariables() {
		assertThat(renderer.variablesOf("Responda em JSON: { \"a\": 1 } e {1abc}")).isEmpty();
	}

	@Test
	void shouldRenderVariables() {
		String rendered = renderer.render("Traduza para {idioma}: {texto}",
				Map.of("idioma", "inglês", "texto", "Olá"));

		assertThat(rendered).isEqualTo("Traduza para inglês: Olá");
	}

	@Test
	void shouldNotInterpretBracesInsideValues() {
		String rendered = renderer.render("Texto: {texto}", Map.of("texto", "valor com {outra} e $1"));

		assertThat(rendered).isEqualTo("Texto: valor com {outra} e $1");
	}

	@Test
	void shouldReportAllMissingVariablesAcrossTemplates() {
		assertThatThrownBy(() -> renderer.requireVariables(Map.of("a", "1"), "{a} {b}", "{c}"))
				.isInstanceOf(MissingVariablesException.class)
				.extracting(ex -> ((MissingVariablesException) ex).getMissing())
				.asList()
				.containsExactly("b", "c");
	}

	@Test
	void shouldReturnNullForNullTemplate() {
		assertThat(renderer.render(null, Map.of())).isNull();
	}

}

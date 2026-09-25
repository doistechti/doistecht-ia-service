package br.com.doistecht.iaservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record TaskRequest(

		@Schema(description = "Valores das variáveis do template", example = """
				{"texto": "O Spring AI é um projeto do ecossistema Spring ...", "linhas": "3"}""")
		@Size(max = 50)
		Map<String, @NotNull @Size(max = 20_000) String> variables,

		@Schema(description = "Provedor de IA (opcional): gemini ou ollama. Sem ele, usa o padrão do cliente ou o global, "
				+ "com troca automática para o provedor reserva se o escolhido estiver fora do ar.", example = "gemini")
		@Pattern(regexp = "[a-z0-9-]{1,30}", message = "nome de provedor inválido")
		String provider) {

	public Map<String, String> variablesOrEmpty() {
		return variables == null ? Map.of() : variables;
	}

}

package br.com.doistecht.iaservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record TaskRequest(

		@Schema(description = "Valores das variáveis do template", example = """
				{"texto": "O Spring AI é um projeto do ecossistema Spring ...", "linhas": "3"}""")
		@Size(max = 50)
		Map<String, @NotNull @Size(max = 20_000) String> variables) {

	public Map<String, String> variablesOrEmpty() {
		return variables == null ? Map.of() : variables;
	}

}

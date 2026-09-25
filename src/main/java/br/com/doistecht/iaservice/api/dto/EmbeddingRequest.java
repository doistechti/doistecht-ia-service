package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.provider.EmbeddingPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;

public record EmbeddingRequest(

		@Schema(description = "Textos para gerar embeddings (até 100)", example = "[\"O Spring AI integra modelos de IA ao Spring.\"]")
		@NotEmpty
		@Size(max = 100)
		List<@NotBlank @Size(max = 8_000) String> texts,

		@Schema(description = "document (padrão) para textos que serão buscados; query para perguntas",
				allowableValues = { "document", "query" }, example = "document")
		@Pattern(regexp = "document|query", message = "deve ser 'document' ou 'query'")
		String purpose,

		@Schema(description = "Provedor de IA (opcional); sem ele, usa o provedor de embeddings do RAG", example = "gemini")
		@Pattern(regexp = "[a-z0-9-]{1,30}", message = "nome de provedor inválido")
		String provider) {

	public EmbeddingPurpose purposeOrDefault() {
		return purpose == null ? EmbeddingPurpose.DOCUMENT : EmbeddingPurpose.valueOf(purpose.toUpperCase(Locale.ROOT));
	}

}

package br.com.doistecht.iaservice.provider;

/**
 * Parte de uma resposta em streaming.
 *
 * @param content trecho de texto (pode ser vazio, ex.: no último evento, que só traz metadados)
 * @param model   modelo que está gerando a resposta
 * @param usage   tokens consumidos até aqui; normalmente só vem preenchido no fim
 */
public record StreamChunk(String content, String model, TokenUsage usage) {

	public StreamChunk(String content) {
		this(content, null, null);
	}

}

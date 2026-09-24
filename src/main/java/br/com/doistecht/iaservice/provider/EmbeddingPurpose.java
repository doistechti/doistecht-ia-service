package br.com.doistecht.iaservice.provider;

/**
 * Uso pretendido do embedding. Modelos como o Gemini geram vetores diferentes para
 * textos que serão buscados e para perguntas, o que melhora a qualidade da busca.
 */
public enum EmbeddingPurpose {

	/** Texto que será indexado e buscado depois (ex.: trechos de documentos). */
	DOCUMENT,

	/** Pergunta usada para buscar documentos. */
	QUERY

}

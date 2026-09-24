package br.com.doistecht.iaservice.rag;

import java.util.List;

/**
 * Resposta de uma pergunta sobre os documentos.
 *
 * @param found    {@code false} quando nenhum trecho relevante foi encontrado (o modelo não é chamado)
 * @param sources  trechos usados como contexto, na ordem em que foram citados ao modelo
 * @param model    modelo que gerou a resposta ({@code null} quando nada foi encontrado)
 * @param fallback {@code true} quando a resposta veio do modelo reserva
 */
public record RagAnswer(String answer, boolean found, List<Source> sources, String model, String provider,
		boolean fallback) {

	/**
	 * @param score   similaridade de cosseno com a pergunta (0 a 1)
	 * @param excerpt início do trecho, para exibição
	 */
	public record Source(Long documentId, String fileName, int chunkIndex, double score, String excerpt) {
	}

	static RagAnswer notFound(String answer) {
		return new RagAnswer(answer, false, List.of(), null, null, false);
	}

}

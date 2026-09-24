package br.com.doistecht.iaservice.provider;

import java.util.List;

/**
 * Embeddings gerados para uma lista de textos, na mesma ordem da entrada.
 *
 * @param vectors vetores normalizados (norma 1)
 * @param model   modelo de embedding utilizado
 * @param usage   tokens consumidos, quando o provedor informa (pode ser nulo)
 */
public record EmbeddingResult(List<float[]> vectors, String model, TokenUsage usage) {

	public int dimensions() {
		return vectors.isEmpty() ? 0 : vectors.getFirst().length;
	}

}

package br.com.doistecht.iaservice;

import br.com.doistecht.iaservice.provider.EmbeddingResult;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Embeddings falsos e determinísticos para testes: cada palavra soma 1 em uma posição do
 * vetor (definida pelo hash da palavra). Textos com palavras em comum ficam parecidos pela
 * similaridade de cosseno, o que basta para testar a busca sem chamar o Gemini.
 */
public final class FakeEmbeddings {

	public static final int DIMENSIONS = 768;

	private FakeEmbeddings() {
	}

	public static EmbeddingResult of(List<String> texts) {
		return new EmbeddingResult(texts.stream().map(FakeEmbeddings::vector).toList(), "fake-embedding", null);
	}

	public static float[] vector(String text) {
		float[] vector = new float[DIMENSIONS];
		String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		for (String word : normalized.split("[^a-z0-9]+")) {
			if (word.length() > 2) {
				vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1;
			}
		}
		double norm = 0;
		for (float value : vector) {
			norm += value * value;
		}
		norm = Math.sqrt(norm);
		for (int i = 0; i < vector.length && norm > 0; i++) {
			vector[i] = (float) (vector[i] / norm);
		}
		return vector;
	}

}

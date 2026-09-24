package br.com.doistecht.iaservice.provider.gemini;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import java.io.IOException;
import java.util.Set;

/**
 * Classifica erros do Gemini em transitórios (vale tentar de novo) ou permanentes.
 * <p>
 * O Spring AI embrulha os erros do SDK em {@code RuntimeException}, por isso a
 * classificação percorre a cadeia de causas.
 */
final class GeminiErrors {

	/** Timeout, limite de requisições do Google e erros do lado do servidor. */
	private static final Set<Integer> TRANSIENT_HTTP_CODES = Set.of(408, 429, 500, 502, 503, 504);

	private GeminiErrors() {
	}

	static boolean isTransient(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof ApiException apiException) {
				return TRANSIENT_HTTP_CODES.contains(apiException.code());
			}
			if (current instanceof GenAiIOException || current instanceof IOException) {
				return true;
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return false;
	}

}

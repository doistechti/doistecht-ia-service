package br.com.doistecht.iaservice.provider.maritaca;

import java.io.IOException;
import java.util.Set;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Classifica erros da Maritaca em transitórios (vale tentar de novo ou usar outro provedor)
 * ou permanentes. Respostas HTTP chegam como {@link RestClientResponseException} (chamadas
 * normais) ou {@link WebClientResponseException} (streaming); falhas de rede, como timeout.
 */
final class MaritacaErrors {

	/** Timeout, limite de requisições e erros do lado do servidor. */
	private static final Set<Integer> TRANSIENT_HTTP_CODES = Set.of(408, 429, 500, 502, 503, 504);

	private MaritacaErrors() {
	}

	static boolean isTransient(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof RestClientResponseException response) {
				return TRANSIENT_HTTP_CODES.contains(response.getStatusCode().value());
			}
			if (current instanceof WebClientResponseException response) {
				return TRANSIENT_HTTP_CODES.contains(response.getStatusCode().value());
			}
			if (current instanceof ResourceAccessException || current instanceof WebClientRequestException
					|| current instanceof IOException) {
				return true;
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return false;
	}

}

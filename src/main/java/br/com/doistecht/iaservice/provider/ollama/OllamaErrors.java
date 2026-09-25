package br.com.doistecht.iaservice.provider.ollama;

import java.io.IOException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Classifica erros do Ollama em transitórios (vale tentar de novo ou usar outro provedor)
 * ou permanentes. O Spring AI já converte respostas 5xx em {@link TransientAiException};
 * servidor desligado e timeout chegam como erros de rede.
 */
final class OllamaErrors {

	private OllamaErrors() {
	}

	static boolean isTransient(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof TransientAiException || current instanceof ResourceAccessException
					|| current instanceof WebClientRequestException || current instanceof IOException) {
				return true;
			}
			if (current instanceof WebClientResponseException response) {
				return response.getStatusCode().is5xxServerError();
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return false;
	}

}

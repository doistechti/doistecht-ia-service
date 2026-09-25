package br.com.doistecht.iaservice.provider.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;

class OllamaErrorsTest {

	@Test
	void shouldTreatServerOfflineAsTransient() {
		assertThat(OllamaErrors.isTransient(
				new ResourceAccessException("I/O error", new ConnectException("Connection refused")))).isTrue();
	}

	@Test
	void shouldTreatServerErrorsAsTransient() {
		assertThat(OllamaErrors.isTransient(new TransientAiException("500 - erro interno"))).isTrue();
	}

	@Test
	void shouldTreatClientErrorsAsPermanent() {
		assertThat(OllamaErrors.isTransient(new NonTransientAiException("404 - model not found"))).isFalse();
		assertThat(OllamaErrors.isTransient(new IllegalArgumentException("bug"))).isFalse();
	}

}

package br.com.doistecht.iaservice.provider.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeminiErrorsTest {

	@ParameterizedTest
	@ValueSource(ints = { 408, 429 })
	void shouldTreatThrottlingAndTimeoutAsTransient(int code) {
		assertThat(GeminiErrors.isTransient(wrapped(new ClientException(code, "erro", "mensagem")))).isTrue();
	}

	@ParameterizedTest
	@ValueSource(ints = { 500, 502, 503, 504 })
	void shouldTreatServerErrorsAsTransient(int code) {
		assertThat(GeminiErrors.isTransient(wrapped(new ServerException(code, "erro", "mensagem")))).isTrue();
	}

	@ParameterizedTest
	@ValueSource(ints = { 400, 401, 403, 404 })
	void shouldTreatClientErrorsAsPermanent(int code) {
		assertThat(GeminiErrors.isTransient(wrapped(new ClientException(code, "erro", "mensagem")))).isFalse();
	}

	@Test
	void shouldTreatNetworkTimeoutAsTransient() {
		assertThat(GeminiErrors.isTransient(wrapped(new SocketTimeoutException("timeout")))).isTrue();
	}

	@Test
	void shouldTreatUnknownErrorsAsPermanent() {
		assertThat(GeminiErrors.isTransient(new IllegalStateException("bug"))).isFalse();
	}

	// O Spring AI embrulha os erros do SDK desta forma
	private static RuntimeException wrapped(Throwable cause) {
		return new RuntimeException("Failed to generate content", cause);
	}

}

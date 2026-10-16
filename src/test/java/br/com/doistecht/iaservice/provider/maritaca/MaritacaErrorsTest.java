package br.com.doistecht.iaservice.provider.maritaca;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class MaritacaErrorsTest {

	@Test
	void shouldTreatNetworkFailuresAsTransient() {
		assertThat(MaritacaErrors.isTransient(
				new ResourceAccessException("I/O error", new ConnectException("Connection refused")))).isTrue();
		assertThat(MaritacaErrors.isTransient(new HttpTimeoutException("request timed out"))).isTrue();
	}

	@Test
	void shouldTreatRateLimitAndServerErrorsAsTransient() {
		assertThat(MaritacaErrors.isTransient(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,
				"Too Many Requests", null, null, null))).isTrue();
		assertThat(MaritacaErrors.isTransient(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE,
				"Service Unavailable", null, null, null))).isTrue();
	}

	@Test
	void shouldTreatClientErrorsAsPermanent() {
		assertThat(MaritacaErrors.isTransient(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
				"Unauthorized", null, null, null))).isFalse();
		assertThat(MaritacaErrors.isTransient(HttpClientErrorException.create(HttpStatus.BAD_REQUEST,
				"Bad Request", null, null, null))).isFalse();
		assertThat(MaritacaErrors.isTransient(new IllegalArgumentException("bug"))).isFalse();
	}

}

package br.com.doistecht.iaservice;

import static org.mockito.BDDMockito.given;

import br.com.doistecht.iaservice.provider.gemini.GeminiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Testes de integração com o {@link GeminiProvider} simulado por Mockito.
 * <p>
 * O {@code GatewayAiProvider} real (cache e registro de uso) envolve o mock, então esses
 * comportamentos também são testados. Para testar o cliente HTTP do Gemini, retry e
 * fallback de verdade, veja {@code GeminiResilienceIT}, que usa WireMock.
 */
public abstract class AbstractMockedProviderIT extends AbstractIntegrationTest {

	@MockitoBean
	protected GeminiProvider gemini;

	@BeforeEach
	void stubProviderName() {
		given(gemini.name()).willReturn("gemini");
	}

}

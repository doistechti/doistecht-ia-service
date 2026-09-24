package br.com.doistecht.iaservice.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import br.com.doistecht.iaservice.client.AuthenticatedClient;
import br.com.doistecht.iaservice.client.ClientAuthenticator;
import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.exception.GlobalExceptionHandler;
import br.com.doistecht.iaservice.ratelimit.RateLimitDecision;
import br.com.doistecht.iaservice.ratelimit.RateLimitService;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base dos testes de controller ({@code @WebMvcTest}): o filtro de autenticação e o
 * tratamento de erros são reais; autenticação de clientes e rate limit são simulados.
 */
@Import({ ApiKeyFilter.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(IaServiceProperties.class)
@TestPropertySource(properties = "ia-service.admin-key=" + ApiControllerTestSupport.ADMIN_KEY)
@MockitoBean(types = { ClientAuthenticator.class, RateLimitService.class })
abstract class ApiControllerTestSupport {

	static final String CLIENT_KEY = "dtia_chave-de-teste";

	static final String ADMIN_KEY = "admin-key-de-teste";

	static final AuthenticatedClient CLIENT = new AuthenticatedClient(7L, "cliente-teste", 10, 200);

	@Autowired
	ClientAuthenticator clientAuthenticator;

	@Autowired
	RateLimitService rateLimitService;

	@BeforeEach
	void authenticateTestClient() {
		given(clientAuthenticator.authenticate(any())).willReturn(Optional.empty());
		given(clientAuthenticator.authenticate(CLIENT_KEY)).willReturn(Optional.of(CLIENT));
		given(rateLimitService.consume(any())).willReturn(new RateLimitDecision(10, 9, 200, 199));
	}

}

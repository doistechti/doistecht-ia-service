package br.com.doistecht.iaservice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base para testes de integração com um PostgreSQL real em container.
 * Os testes são ignorados quando o Docker não está disponível.
 */
@SpringBootTest(properties = {
		"spring.ai.google.genai.api-key=test-gemini-key",
		"ia-service.api-key=" + AbstractPostgresIntegrationTest.API_KEY
})
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractPostgresIntegrationTest {

	protected static final String API_KEY = "test-api-key";

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

}

package br.com.doistecht.iaservice;

import br.com.doistecht.iaservice.client.ClientService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base para testes de integração com PostgreSQL e Redis reais em containers.
 * Os testes são ignorados quando o Docker não está disponível.
 * <p>
 * O contexto do Spring é compartilhado entre as classes com a mesma configuração; por isso
 * cada teste cria seus próprios clientes (com nomes únicos), evitando interferência de
 * limites e cache.
 */
@SpringBootTest(properties = {
		"spring.ai.google.genai.api-key=test-gemini-key",
		"ia-service.admin-key=" + AbstractIntegrationTest.ADMIN_KEY
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

	protected static final String ADMIN_KEY = "admin-key-de-teste";

	// Containers "singleton": sobem uma vez por execução e são reaproveitados por todas as classes
	// de teste, assim como o contexto do Spring. Com @Container eles seriam parados ao fim de cada
	// classe, e o contexto em cache ficaria apontando para containers que não existem mais.
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@ServiceConnection(name = "redis")
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

	static {
		POSTGRES.start();
		REDIS.start();
	}

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected ClientService clientService;

	/** Cria um cliente com nome único e retorna seu id e API key. */
	protected TestClient createClient(int rateLimitPerMinute, int dailyQuota) {
		var created = clientService.create("cliente-" + UUID.randomUUID(), rateLimitPerMinute, dailyQuota);
		return new TestClient(created.client().getId(), created.apiKey().value());
	}

	protected TestClient createClient() {
		return createClient(1_000, 10_000);
	}

	protected record TestClient(Long id, String apiKey) {
	}

}

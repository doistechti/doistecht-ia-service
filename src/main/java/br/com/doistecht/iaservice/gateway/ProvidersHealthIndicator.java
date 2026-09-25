package br.com.doistecht.iaservice.gateway;

import br.com.doistecht.iaservice.provider.ModelProvider;
import br.com.doistecht.iaservice.provider.ProviderHealth;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Situação dos provedores de IA em {@code /actuator/health} (componente {@code aiProviders}).
 * <p>
 * Fica {@code UP} enquanto pelo menos um provedor responde: com o provedor reserva, o serviço
 * continua atendendo mesmo com o principal fora do ar. Um provedor opcional desligado não deve
 * derrubar o health check do serviço inteiro.
 */
@Component("aiProviders")
class ProvidersHealthIndicator implements HealthIndicator {

	private final ProviderRouter router;

	ProvidersHealthIndicator(ProviderRouter router) {
		this.router = router;
	}

	@Override
	public Health health() {
		Map<String, Object> details = new LinkedHashMap<>();
		boolean anyAvailable = false;
		for (ModelProvider provider : router.all()) {
			ProviderHealth health = provider.health();
			details.put(provider.name(), Map.of("status", health.status().name(), "detail", health.detail()));
			anyAvailable |= health.isAvailable();
		}
		return (anyAvailable ? Health.up() : Health.down()).withDetails(details).build();
	}

}

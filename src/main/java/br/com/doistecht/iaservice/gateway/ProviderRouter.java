package br.com.doistecht.iaservice.gateway;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.provider.ModelProvider;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Escolhe o provedor de cada chamada, nesta ordem de prioridade:
 * <ol>
 * <li>o provedor pedido na requisição (campo {@code provider});</li>
 * <li>o provedor padrão do cliente;</li>
 * <li>o provedor padrão global ({@code ia-service.providers.default-provider}).</li>
 * </ol>
 * Quando o cliente não pediu um provedor específico, o reserva
 * ({@code ia-service.providers.fallback}) é usado se o escolhido estiver fora do ar.
 * Um pedido explícito é respeitado: não há troca silenciosa de provedor.
 */
@Component
public class ProviderRouter {

	private static final Logger log = LoggerFactory.getLogger(ProviderRouter.class);

	// Poucos provedores: a busca pelo nome a cada chamada é barata e dispensa montar um mapa na subida
	private final List<ModelProvider> providers;

	private final String defaultProvider;

	private final String fallbackProvider;

	private final String embeddingProvider;

	/**
	 * @param primary  provedor que será chamado
	 * @param fallback provedor reserva, ou {@code null} quando não há troca permitida
	 */
	public record Selection(ModelProvider primary, ModelProvider fallback) {
	}

	public ProviderRouter(List<ModelProvider> providers, IaServiceProperties properties) {
		this.providers = List.copyOf(providers);
		this.defaultProvider = properties.providers().defaultProvider();
		this.embeddingProvider = properties.rag().embeddingProvider();
		String fallback = properties.providers().fallback();
		this.fallbackProvider = StringUtils.hasText(fallback) ? fallback : null;
	}

	/** Avisa na subida sobre configurações que não vão funcionar como esperado. */
	@EventListener(ApplicationReadyEvent.class)
	void checkConfiguration() {
		if (find(defaultProvider).isEmpty()) {
			log.error("Provedor padrão '{}' não está habilitado. Disponíveis: {}", defaultProvider, names());
		}
		if (fallbackProvider != null && find(fallbackProvider).isEmpty()) {
			log.warn("Provedor reserva '{}' não está habilitado; não haverá troca entre provedores", fallbackProvider);
		}
	}

	/**
	 * @param requested     provedor pedido na requisição, ou {@code null}
	 * @param clientDefault provedor padrão do cliente, ou {@code null}
	 * @throws UnknownProviderException se o provedor pedido não existir ou não estiver habilitado
	 */
	public Selection select(String requested, String clientDefault) {
		if (StringUtils.hasText(requested)) {
			return new Selection(require(requested), null);
		}
		ModelProvider primary = Optional.ofNullable(clientDefault)
				.flatMap(this::find)
				.orElseGet(() -> {
					if (clientDefault != null) {
						log.warn("Provedor padrão do cliente '{}' não está habilitado; usando '{}'", clientDefault,
								defaultProvider);
					}
					return require(defaultProvider);
				});
		ModelProvider fallback = fallbackProvider == null || fallbackProvider.equals(primary.name())
				? null
				: find(fallbackProvider).orElse(null);
		return new Selection(primary, fallback);
	}

	/** Provedor de embeddings: o pedido, ou o fixo do RAG. */
	public ModelProvider embeddingProvider(String requested) {
		return require(StringUtils.hasText(requested) ? requested : embeddingProvider);
	}

	public boolean isAvailable(String name) {
		return find(name).isPresent();
	}

	public List<ModelProvider> all() {
		return providers;
	}

	public String defaultProvider() {
		return defaultProvider;
	}

	/** Provedor reserva, se estiver configurado e habilitado. */
	public String fallbackProvider() {
		return fallbackProvider != null && isAvailable(fallbackProvider) ? fallbackProvider : null;
	}

	private Optional<ModelProvider> find(String name) {
		return providers.stream().filter(provider -> name.equals(provider.name())).findFirst();
	}

	private ModelProvider require(String name) {
		return find(name).orElseThrow(() -> new UnknownProviderException(name, names()));
	}

	private List<String> names() {
		return providers.stream().map(ModelProvider::name).toList();
	}

}

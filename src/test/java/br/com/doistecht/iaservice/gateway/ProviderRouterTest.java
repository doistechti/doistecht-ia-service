package br.com.doistecht.iaservice.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.config.TestProperties;
import br.com.doistecht.iaservice.provider.ModelProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderRouterTest {

	private final ModelProvider gemini = provider("gemini");

	private final ModelProvider ollama = provider("ollama");

	@Test
	void shouldUseGlobalDefaultWithFallbackWhenNothingIsRequested() {
		ProviderRouter router = router("gemini", "ollama", gemini, ollama);

		var selection = router.select(null, null);

		assertThat(selection.primary()).isSameAs(gemini);
		assertThat(selection.fallback()).isSameAs(ollama);
	}

	@Test
	void shouldPreferClientDefaultOverGlobalDefault() {
		ProviderRouter router = router("gemini", "ollama", gemini, ollama);

		var selection = router.select(null, "ollama");

		assertThat(selection.primary()).isSameAs(ollama);
		// O reserva é o próprio provedor escolhido: não há para onde trocar
		assertThat(selection.fallback()).isNull();
	}

	@Test
	void shouldRespectExplicitRequestWithoutFallback() {
		ProviderRouter router = router("gemini", "ollama", gemini, ollama);

		var selection = router.select("gemini", "ollama");

		assertThat(selection.primary()).isSameAs(gemini);
		assertThat(selection.fallback()).isNull();
	}

	@Test
	void shouldIgnoreDisabledClientDefault() {
		ProviderRouter router = router("gemini", null, gemini);

		assertThat(router.select(null, "ollama").primary()).isSameAs(gemini);
	}

	@Test
	void shouldIgnoreDisabledFallback() {
		ProviderRouter router = router("gemini", "ollama", gemini);

		assertThat(router.select(null, null).fallback()).isNull();
		assertThat(router.fallbackProvider()).isNull();
	}

	@Test
	void shouldRejectUnknownRequestedProvider() {
		ProviderRouter router = router("gemini", null, gemini, ollama);

		assertThatThrownBy(() -> router.select("openai", null))
				.isInstanceOf(UnknownProviderException.class)
				.hasMessageContaining("openai")
				.hasMessageContaining("gemini, ollama");
	}

	@Test
	void shouldUseRagEmbeddingProviderByDefault() {
		ProviderRouter router = router("ollama", null, gemini, ollama);

		assertThat(router.embeddingProvider(null)).isSameAs(gemini);
		assertThat(router.embeddingProvider("ollama")).isSameAs(ollama);
	}

	private static ProviderRouter router(String defaultProvider, String fallback, ModelProvider... providers) {
		IaServiceProperties defaults = TestProperties.defaults();
		IaServiceProperties properties = new IaServiceProperties(defaults.adminKey(), defaults.auth(), defaults.cache(),
				defaults.pricing(), defaults.rag(), new IaServiceProperties.Providers(defaultProvider, fallback));
		return new ProviderRouter(List.of(providers), properties);
	}

	private static ModelProvider provider(String name) {
		ModelProvider provider = mock(ModelProvider.class);
		given(provider.name()).willReturn(name);
		return provider;
	}

}

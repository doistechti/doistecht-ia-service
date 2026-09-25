package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.provider.ModelProvider;
import br.com.doistecht.iaservice.provider.ProviderHealth;
import br.com.doistecht.iaservice.provider.ProviderInfo;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProviderResponse(

		@Schema(description = "Nome usado no campo provider das requisições", example = "gemini")
		String name,

		@Schema(description = "Provedor usado quando a requisição e o cliente não escolhem um")
		boolean defaultProvider,

		@Schema(description = "Provedor reserva, usado quando o escolhido está fora do ar")
		boolean fallbackProvider,

		ProviderInfo models,

		@Schema(description = "UP, DEGRADED (só parte dos modelos disponível) ou DOWN")
		ProviderHealth.Status status,

		String detail) {

	public static ProviderResponse from(ModelProvider provider, boolean isDefault, boolean isFallback) {
		ProviderHealth health = provider.health();
		return new ProviderResponse(provider.name(), isDefault, isFallback, provider.info(), health.status(),
				health.detail());
	}

}

package br.com.doistecht.iaservice.config;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configurações próprias do serviço (prefixo {@code ia-service}).
 *
 * @param adminKey chave de administrador, exigida nas rotas {@code /v1/admin/**}
 * @param auth     autenticação de clientes
 * @param cache    cache de respostas
 * @param pricing  preço por milhão de tokens de cada modelo, usado para estimar custo
 */
@Validated
@ConfigurationProperties(prefix = "ia-service")
public record IaServiceProperties(
		@NotBlank String adminKey,
		@DefaultValue Auth auth,
		@DefaultValue Cache cache,
		Map<String, ModelPrice> pricing) {

	public IaServiceProperties {
		pricing = pricing == null ? Map.of() : Map.copyOf(pricing);
	}

	/** @param keyCacheTtl por quanto tempo o resultado da validação de uma chave fica em memória */
	public record Auth(@DefaultValue("30s") Duration keyCacheTtl) {
	}

	public record Cache(@DefaultValue("true") boolean enabled, @DefaultValue("1h") Duration ttl) {
	}

	public record ModelPrice(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
	}

}

package br.com.doistecht.iaservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configurações próprias do serviço (prefixo {@code ia-service}).
 *
 * @param apiKey chave que os clientes devem enviar no header {@code X-API-Key}
 */
@Validated
@ConfigurationProperties(prefix = "ia-service")
public record IaServiceProperties(@NotBlank String apiKey) {
}

package br.com.doistecht.iaservice.provider.gemini;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurações do cliente Gemini (prefixo {@code ia-service.gemini}).
 *
 * @param baseUrl       endereço da API; vazio usa o padrão do Google (os testes apontam para o WireMock)
 * @param timeout       tempo máximo de cada chamada HTTP ao Gemini
 * @param fallbackModel modelo reserva usado quando o principal está indisponível; vazio desativa
 */
@ConfigurationProperties(prefix = "ia-service.gemini")
public record GeminiProperties(
		String baseUrl,
		@DefaultValue("30s") Duration timeout,
		String fallbackModel) {
}

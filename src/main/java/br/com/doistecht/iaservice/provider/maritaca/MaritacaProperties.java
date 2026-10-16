package br.com.doistecht.iaservice.provider.maritaca;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurações do provedor Maritaca (prefixo {@code ia-service.maritaca}).
 *
 * @param enabled           liga o provedor; desligado, ele não aparece em {@code /v1/providers}
 * @param apiKey            chave criada em plataforma.maritaca.ai
 * @param baseUrl           endereço da API, compatível com a da OpenAI
 * @param chatModel         modelo de chat
 * @param fallbackChatModel modelo reserva da própria Maritaca; vazio desativa
 * @param connectTimeout    tempo máximo para conectar
 * @param readTimeout       tempo máximo de resposta
 */
@ConfigurationProperties(prefix = "ia-service.maritaca")
public record MaritacaProperties(
		@DefaultValue("false") boolean enabled,
		String apiKey,
		@DefaultValue("https://chat.maritaca.ai/api") String baseUrl,
		@DefaultValue("sabiazinho-4") String chatModel,
		String fallbackChatModel,
		@DefaultValue("5s") Duration connectTimeout,
		@DefaultValue("60s") Duration readTimeout) {
}

package br.com.doistecht.iaservice.provider.ollama;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurações do provedor Ollama (prefixo {@code ia-service.ollama}). O endereço do
 * servidor vem de {@code spring.ai.ollama.base-url}.
 *
 * @param enabled        liga o provedor; desligado, ele não aparece em {@code /v1/providers}
 * @param chatModel      modelo de chat (precisa estar baixado no Ollama)
 * @param embeddingModel modelo de embeddings (precisa estar baixado no Ollama)
 * @param connectTimeout tempo máximo para conectar: curto, para falhar rápido com o Ollama desligado
 * @param readTimeout    tempo máximo de resposta: longo, porque modelos locais em CPU são lentos
 */
@ConfigurationProperties(prefix = "ia-service.ollama")
public record OllamaProperties(
		@DefaultValue("false") boolean enabled,
		@DefaultValue("llama3.2:3b") String chatModel,
		@DefaultValue("nomic-embed-text") String embeddingModel,
		@DefaultValue("2s") Duration connectTimeout,
		@DefaultValue("120s") Duration readTimeout) {
}

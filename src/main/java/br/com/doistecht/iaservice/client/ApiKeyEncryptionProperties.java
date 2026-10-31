package br.com.doistecht.iaservice.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param key chave AES-256 em Base64 (32 bytes) usada para guardar as API keys de forma recuperável;
 *            vazia desativa: as chaves voltam a ser exibidas só no cadastro e na rotação
 */
@ConfigurationProperties(prefix = "ia-service.api-key-encryption")
public record ApiKeyEncryptionProperties(String key) {
}

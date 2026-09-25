package br.com.doistecht.iaservice.provider;

/**
 * Modelos configurados em um provedor.
 *
 * @param chatModel         modelo principal de chat
 * @param fallbackChatModel modelo reserva do próprio provedor, se houver
 * @param embeddingModel    modelo de embeddings
 */
public record ProviderInfo(String chatModel, String fallbackChatModel, String embeddingModel) {
}

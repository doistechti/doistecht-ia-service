package br.com.doistecht.iaservice.provider;

/**
 * Resposta de chat independente de provedor.
 *
 * @param content  texto gerado pelo modelo
 * @param model    modelo que gerou a resposta
 * @param provider provedor utilizado
 */
public record ChatResult(String content, String model, String provider) {
}

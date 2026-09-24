package br.com.doistecht.iaservice.structured;

import tools.jackson.databind.JsonNode;

/**
 * Resposta estruturada já validada contra o schema.
 *
 * @param data     JSON gerado pelo modelo
 * @param model    modelo que gerou a resposta
 * @param provider provedor utilizado
 */
public record StructuredResult(JsonNode data, String model, String provider) {
}

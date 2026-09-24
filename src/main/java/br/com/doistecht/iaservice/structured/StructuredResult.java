package br.com.doistecht.iaservice.structured;

import tools.jackson.databind.JsonNode;

/**
 * Resposta estruturada já validada contra o schema.
 *
 * @param data     JSON gerado pelo modelo
 * @param model    modelo que gerou a resposta
 * @param provider provedor utilizado
 * @param fallback {@code true} quando a resposta veio do modelo reserva
 */
public record StructuredResult(JsonNode data, String model, String provider, boolean fallback) {

	public StructuredResult(JsonNode data, String model, String provider) {
		this(data, model, provider, false);
	}

}

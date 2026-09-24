package br.com.doistecht.iaservice.template;

import tools.jackson.databind.JsonNode;

/**
 * Dados para criar uma nova versão de template.
 *
 * @param clientId     cliente dono do template, ou {@code null} para um template global
 * @param outputSchema JSON Schema da resposta (opcional)
 */
public record NewPromptTemplate(Long clientId, String name, String systemPrompt, String userPromptTemplate,
		JsonNode outputSchema) {
}

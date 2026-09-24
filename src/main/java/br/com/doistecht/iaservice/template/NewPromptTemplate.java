package br.com.doistecht.iaservice.template;

import tools.jackson.databind.JsonNode;

/**
 * Dados para criar uma nova versão de template.
 *
 * @param outputSchema JSON Schema da resposta (opcional)
 */
public record NewPromptTemplate(String name, String systemPrompt, String userPromptTemplate, JsonNode outputSchema) {
}

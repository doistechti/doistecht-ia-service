package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.template.PromptTemplate;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record TemplateResponse(
		Long id,
		Long clientId,
		String name,
		int version,
		String systemPrompt,
		String userPromptTemplate,
		List<String> variables,
		JsonNode outputSchema,
		boolean active,
		Instant createdAt) {

	public static TemplateResponse from(PromptTemplate template, List<String> variables, JsonNode outputSchema) {
		return new TemplateResponse(template.getId(), template.getClientId(), template.getName(), template.getVersion(),
				template.getSystemPrompt(), template.getUserPromptTemplate(), variables, outputSchema,
				template.isActive(), template.getCreatedAt());
	}

}

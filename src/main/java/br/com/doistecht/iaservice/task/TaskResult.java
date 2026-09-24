package br.com.doistecht.iaservice.task;

import tools.jackson.databind.JsonNode;

/**
 * Resultado da execução de uma tarefa. Apenas um entre {@code content} (texto)
 * e {@code data} (JSON estruturado) é preenchido, conforme o template.
 */
public record TaskResult(String template, int version, String content, JsonNode data, String model,
		String provider, boolean fallback) {

	public TaskResult(String template, int version, String content, JsonNode data, String model, String provider) {
		this(template, version, content, data, model, provider, false);
	}

}

package br.com.doistecht.iaservice.task;

import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.structured.StructuredOutputService;
import br.com.doistecht.iaservice.structured.StructuredResult;
import br.com.doistecht.iaservice.template.PromptTemplate;
import br.com.doistecht.iaservice.template.PromptTemplateService;
import br.com.doistecht.iaservice.template.TemplateRenderer;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Executa tarefas prontas: o cliente informa só o nome do template e as variáveis.
 */
@Service
public class TaskService {

	private final PromptTemplateService templateService;

	private final TemplateRenderer renderer;

	private final AiProvider aiProvider;

	private final StructuredOutputService structuredOutputService;

	private final ObjectMapper objectMapper;

	public TaskService(PromptTemplateService templateService, TemplateRenderer renderer, AiProvider aiProvider,
			StructuredOutputService structuredOutputService, ObjectMapper objectMapper) {
		this.templateService = templateService;
		this.renderer = renderer;
		this.aiProvider = aiProvider;
		this.structuredOutputService = structuredOutputService;
		this.objectMapper = objectMapper;
	}

	/**
	 * @param clientId cliente que executa a tarefa; templates próprios dele têm prioridade sobre os globais
	 */
	public TaskResult execute(String name, Integer version, Long clientId, Map<String, String> variables) {
		PromptTemplate template = templateService.resolve(name, version, clientId);

		// Valida os dois textos juntos para devolver todas as variáveis ausentes de uma vez
		renderer.requireVariables(variables, template.getSystemPrompt(), template.getUserPromptTemplate());
		var command = new ChatCommand(
				renderer.render(template.getSystemPrompt(), variables),
				renderer.render(template.getUserPromptTemplate(), variables));

		if (template.getOutputSchema() != null) {
			StructuredResult result = structuredOutputService.generate(command,
					objectMapper.readTree(template.getOutputSchema()));
			return new TaskResult(template.getName(), template.getVersion(), null, result.data(), result.model(),
					result.provider(), result.fallback());
		}

		ChatResult result = aiProvider.chat(command);
		return new TaskResult(template.getName(), template.getVersion(), result.content(), null, result.model(),
				result.provider(), result.fallback());
	}

}

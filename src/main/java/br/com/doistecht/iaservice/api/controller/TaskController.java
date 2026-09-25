package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.TaskRequest;
import br.com.doistecht.iaservice.api.dto.TaskResponse;
import br.com.doistecht.iaservice.client.AuthenticatedClient;
import br.com.doistecht.iaservice.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tasks", description = "Tarefas prontas baseadas em templates de prompt")
@RestController
@RequestMapping("/v1/tasks")
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@Operation(summary = "Executa uma tarefa a partir do template e das variáveis informadas")
	@PostMapping("/{template}")
	public TaskResponse execute(
			@Parameter(description = "Nome do template", example = "resumir-texto") @PathVariable String template,
			@Parameter(description = "Versão do template; se omitida, usa a versão ativa mais recente")
			@RequestParam(required = false) Integer version,
			@Valid @RequestBody(required = false) TaskRequest request,
			@Parameter(hidden = true) @RequestAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE) AuthenticatedClient client) {
		var variables = request == null ? Map.<String, String>of() : request.variablesOrEmpty();
		String provider = request == null ? null : request.provider();
		return TaskResponse.from(taskService.execute(template, version, client.id(), variables, provider));
	}

}

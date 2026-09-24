package br.com.doistecht.iaservice.template;

import br.com.doistecht.iaservice.structured.JsonSchemaValidator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PromptTemplateService {

	private final PromptTemplateRepository repository;

	private final JsonSchemaValidator schemaValidator;

	public PromptTemplateService(PromptTemplateRepository repository, JsonSchemaValidator schemaValidator) {
		this.repository = repository;
		this.schemaValidator = schemaValidator;
	}

	/**
	 * Busca a versão pedida, ou a versão ativa mais recente quando {@code version} é nulo.
	 * Versões inativas não podem ser executadas.
	 */
	public PromptTemplate resolve(String name, Integer version) {
		var template = version == null
				? repository.findFirstByNameAndActiveTrueOrderByVersionDesc(name)
				: repository.findByNameAndVersion(name, version).filter(PromptTemplate::isActive);
		return template.orElseThrow(() -> new TemplateNotFoundException(name, version));
	}

	public List<PromptTemplate> listAll() {
		return repository.findAllByOrderByNameAscVersionDesc();
	}

	public List<PromptTemplate> listVersions(String name) {
		List<PromptTemplate> versions = repository.findByNameOrderByVersionDesc(name);
		if (versions.isEmpty()) {
			throw new TemplateNotFoundException(name, null);
		}
		return versions;
	}

	/** Cria a próxima versão do template (1 se o nome ainda não existir). */
	@Transactional
	public PromptTemplate create(NewPromptTemplate command) {
		String outputSchema = null;
		if (command.outputSchema() != null && !command.outputSchema().isNull()) {
			schemaValidator.compile(command.outputSchema());
			outputSchema = command.outputSchema().toString();
		}

		int nextVersion = repository.findFirstByNameOrderByVersionDesc(command.name())
				.map(latest -> latest.getVersion() + 1)
				.orElse(1);

		return repository.save(new PromptTemplate(command.name(), nextVersion, command.systemPrompt(),
				command.userPromptTemplate(), outputSchema));
	}

	@Transactional
	public PromptTemplate setActive(String name, int version, boolean active) {
		PromptTemplate template = repository.findByNameAndVersion(name, version)
				.orElseThrow(() -> new TemplateNotFoundException(name, version));
		template.setActive(active);
		return template;
	}

}

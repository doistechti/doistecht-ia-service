package br.com.doistecht.iaservice.template;

import br.com.doistecht.iaservice.client.ClientNotFoundException;
import br.com.doistecht.iaservice.client.ClientRepository;
import br.com.doistecht.iaservice.structured.JsonSchemaValidator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Templates podem ser globais ({@code clientId} nulo) ou de um cliente. Quando um cliente
 * tem um template com o mesmo nome de um global, o dele substitui o global por completo.
 */
@Service
@Transactional(readOnly = true)
public class PromptTemplateService {

	private final PromptTemplateRepository repository;

	private final ClientRepository clientRepository;

	private final JsonSchemaValidator schemaValidator;

	public PromptTemplateService(PromptTemplateRepository repository, ClientRepository clientRepository,
			JsonSchemaValidator schemaValidator) {
		this.repository = repository;
		this.clientRepository = clientRepository;
		this.schemaValidator = schemaValidator;
	}

	/**
	 * Busca o template que o cliente deve executar: a versão pedida, ou a versão ativa mais
	 * recente quando {@code version} é nulo. Versões inativas não podem ser executadas.
	 */
	public PromptTemplate resolve(String name, Integer version, Long clientId) {
		Long scope = clientId != null && repository.existsByClientIdAndName(clientId, name) ? clientId : null;
		var template = version == null
				? repository.findFirstByClientIdAndNameAndActiveTrueOrderByVersionDesc(scope, name)
				: repository.findByClientIdAndNameAndVersion(scope, name, version).filter(PromptTemplate::isActive);
		return template.orElseThrow(() -> new TemplateNotFoundException(name, version));
	}

	public List<PromptTemplate> listAll() {
		return repository.findAllByOrderByNameAscClientIdAscVersionDesc();
	}

	public List<PromptTemplate> listVersions(String name, Long clientId) {
		List<PromptTemplate> versions = repository.findByClientIdAndNameOrderByVersionDesc(clientId, name);
		if (versions.isEmpty()) {
			throw new TemplateNotFoundException(name, null);
		}
		return versions;
	}

	/** Cria a próxima versão do template no escopo informado (1 se o nome ainda não existir). */
	@Transactional
	public PromptTemplate create(NewPromptTemplate command) {
		if (command.clientId() != null && !clientRepository.existsById(command.clientId())) {
			throw new ClientNotFoundException(command.clientId());
		}

		String outputSchema = null;
		if (command.outputSchema() != null && !command.outputSchema().isNull()) {
			schemaValidator.compile(command.outputSchema());
			outputSchema = command.outputSchema().toString();
		}

		int nextVersion = repository.findFirstByClientIdAndNameOrderByVersionDesc(command.clientId(), command.name())
				.map(latest -> latest.getVersion() + 1)
				.orElse(1);

		return repository.save(new PromptTemplate(command.clientId(), command.name(), nextVersion,
				command.systemPrompt(), command.userPromptTemplate(), outputSchema));
	}

	@Transactional
	public PromptTemplate setActive(String name, int version, Long clientId, boolean active) {
		PromptTemplate template = repository.findByClientIdAndNameAndVersion(clientId, name, version)
				.orElseThrow(() -> new TemplateNotFoundException(name, version));
		template.setActive(active);
		return template;
	}

}

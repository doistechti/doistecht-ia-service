package br.com.doistecht.iaservice.structured;

import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import com.networknt.schema.Schema;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Gera respostas em JSON e garante que elas seguem o schema pedido.
 * <p>
 * Se o modelo devolver algo fora do schema, tenta mais uma vez antes de desistir.
 */
@Service
public class StructuredOutputService {

	private static final Logger log = LoggerFactory.getLogger(StructuredOutputService.class);

	static final int MAX_ATTEMPTS = 2;

	private final AiProvider aiProvider;

	private final JsonSchemaValidator validator;

	private final ObjectMapper objectMapper;

	public StructuredOutputService(AiProvider aiProvider, JsonSchemaValidator validator, ObjectMapper objectMapper) {
		this.aiProvider = aiProvider;
		this.validator = validator;
		this.objectMapper = objectMapper;
	}

	public StructuredResult generate(ChatCommand command, JsonNode schemaNode) {
		Schema schema = validator.compile(schemaNode);
		String schemaJson = schemaNode.toString();

		List<String> errors = List.of();
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			ChatResult result = aiProvider.structured(command, schemaJson);

			JsonNode data = parse(result.content());
			if (data == null) {
				errors = List.of("A resposta do modelo não é um JSON válido.");
			}
			else {
				errors = validator.validate(schema, data);
				if (errors.isEmpty()) {
					return new StructuredResult(data, result.model(), result.provider());
				}
			}
			log.warn("Resposta estruturada inválida (tentativa {}/{}): {}", attempt, MAX_ATTEMPTS, errors);
		}
		throw new InvalidStructuredOutputException(errors);
	}

	private JsonNode parse(String content) {
		if (content == null || content.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readTree(content);
		}
		catch (JacksonException ex) {
			return null;
		}
	}

}

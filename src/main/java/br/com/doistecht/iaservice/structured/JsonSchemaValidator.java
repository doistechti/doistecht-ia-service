package br.com.doistecht.iaservice.structured;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Compila JSON Schemas enviados pelos clientes e valida documentos contra eles.
 */
@Component
public class JsonSchemaValidator {

	private static final int MAX_ERRORS = 10;

	private static final List<String> REF_KEYWORDS = List.of("$ref", "$dynamicRef", "$recursiveRef");

	private final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

	public Schema compile(JsonNode schemaNode) {
		if (schemaNode == null || !schemaNode.isObject()) {
			throw new InvalidSchemaException("O schema deve ser um objeto JSON.");
		}
		rejectExternalRefs(schemaNode);
		try {
			return registry.getSchema(schemaNode);
		}
		catch (RuntimeException ex) {
			throw new InvalidSchemaException("Schema inválido: " + ex.getMessage());
		}
	}

	/** Retorna as violações encontradas; lista vazia significa documento válido. */
	public List<String> validate(Schema schema, JsonNode document) {
		return schema.validate(document).stream()
				.limit(MAX_ERRORS)
				.map(error -> location(error.getInstanceLocation().toString()) + ": " + error.getMessage())
				.toList();
	}

	// A raiz do documento vem como caminho vazio; "$" deixa a mensagem mais clara
	private static String location(String path) {
		return path.isEmpty() ? "$" : path;
	}

	// O schema vem do cliente: referências externas fariam o serviço baixar URLs arbitrárias (SSRF)
	private static void rejectExternalRefs(JsonNode node) {
		if (node.isObject()) {
			for (var property : node.properties()) {
				if (REF_KEYWORDS.contains(property.getKey()) && !property.getValue().asString("").startsWith("#")) {
					throw new InvalidSchemaException("Referências externas ($ref) não são permitidas no schema.");
				}
				rejectExternalRefs(property.getValue());
			}
		}
		else if (node.isArray()) {
			for (JsonNode element : node) {
				rejectExternalRefs(element);
			}
		}
	}

}

package br.com.doistecht.iaservice.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.networknt.schema.Schema;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class JsonSchemaValidatorTest {

	private final ObjectMapper mapper = JsonMapper.builder().build();

	private final JsonSchemaValidator validator = new JsonSchemaValidator();

	private final JsonNode personSchema = json("""
			{
			  "type": "object",
			  "properties": { "nome": { "type": "string" }, "idade": { "type": "integer" } },
			  "required": ["nome", "idade"]
			}""");

	@Test
	void shouldAcceptValidDocument() {
		Schema schema = validator.compile(personSchema);

		assertThat(validator.validate(schema, json("{\"nome\": \"Ana\", \"idade\": 30}"))).isEmpty();
	}

	@Test
	void shouldReportViolations() {
		Schema schema = validator.compile(personSchema);

		assertThat(validator.validate(schema, json("{\"nome\": \"Ana\", \"idade\": \"trinta\"}")))
				.hasSize(1)
				.first().asString().contains("idade");
	}

	@Test
	void shouldRejectNonObjectSchema() {
		assertThatThrownBy(() -> validator.compile(json("[1, 2]")))
				.isInstanceOf(InvalidSchemaException.class);
	}

	@Test
	void shouldRejectExternalReferences() {
		JsonNode schema = json("""
				{"type": "object", "properties": {"a": {"$ref": "https://example.com/schema.json"}}}""");

		assertThatThrownBy(() -> validator.compile(schema))
				.isInstanceOf(InvalidSchemaException.class)
				.hasMessageContaining("$ref");
	}

	@Test
	void shouldAllowLocalReferences() {
		JsonNode schema = json("""
				{"$defs": {"nome": {"type": "string"}}, "type": "object",
				 "properties": {"a": {"$ref": "#/$defs/nome"}}}""");

		Schema compiled = validator.compile(schema);

		assertThat(validator.validate(compiled, json("{\"a\": 1}"))).isNotEmpty();
	}

	private JsonNode json(String content) {
		return mapper.readTree(content);
	}

}

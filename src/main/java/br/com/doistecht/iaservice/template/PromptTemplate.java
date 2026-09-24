package br.com.doistecht.iaservice.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Template de prompt versionado. Cada alteração gera uma nova versão;
 * versões existentes nunca são editadas, apenas ativadas ou desativadas.
 */
@Entity
@Table(name = "prompt_template")
public class PromptTemplate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Cliente dono do template; {@code null} para templates globais. */
	private Long clientId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false)
	private int version;

	@Column(columnDefinition = "text")
	private String systemPrompt;

	@Column(nullable = false, columnDefinition = "text")
	private String userPromptTemplate;

	/** JSON Schema da resposta; quando presente, a tarefa retorna JSON estruturado. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String outputSchema;

	@Column(nullable = false)
	private boolean active = true;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected PromptTemplate() {
	}

	public PromptTemplate(String name, int version, String systemPrompt, String userPromptTemplate,
			String outputSchema) {
		this(null, name, version, systemPrompt, userPromptTemplate, outputSchema);
	}

	public PromptTemplate(Long clientId, String name, int version, String systemPrompt, String userPromptTemplate,
			String outputSchema) {
		this.clientId = clientId;
		this.name = name;
		this.version = version;
		this.systemPrompt = systemPrompt;
		this.userPromptTemplate = userPromptTemplate;
		this.outputSchema = outputSchema;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getClientId() {
		return clientId;
	}

	public String getName() {
		return name;
	}

	public int getVersion() {
		return version;
	}

	public String getSystemPrompt() {
		return systemPrompt;
	}

	public String getUserPromptTemplate() {
		return userPromptTemplate;
	}

	public String getOutputSchema() {
		return outputSchema;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

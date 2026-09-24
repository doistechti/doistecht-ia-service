package br.com.doistecht.iaservice.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Documento enviado por um cliente para ser consultado via RAG. Os trechos e embeddings
 * ficam na tabela {@code document_chunk}, gravada pelo {@link DocumentChunkStore}.
 */
@Entity
@Table(name = "document")
public class Document {

	private static final int MAX_ERROR_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long clientId;

	@Column(nullable = false)
	private String fileName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DocumentType contentType;

	@Column(nullable = false)
	private long sizeBytes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DocumentStatus status;

	@Column(length = MAX_ERROR_LENGTH)
	private String errorMessage;

	private Integer chunkCount;

	@Column(length = 100)
	private String embeddingModel;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant processedAt;

	protected Document() {
	}

	public Document(Long clientId, String fileName, DocumentType contentType, long sizeBytes) {
		this.clientId = clientId;
		this.fileName = fileName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.status = DocumentStatus.PROCESSING;
		this.createdAt = Instant.now();
	}

	void markReady(int chunkCount, String embeddingModel) {
		this.status = DocumentStatus.READY;
		this.chunkCount = chunkCount;
		this.embeddingModel = embeddingModel;
		this.errorMessage = null;
		this.processedAt = Instant.now();
	}

	void markFailed(String errorMessage) {
		this.status = DocumentStatus.FAILED;
		this.errorMessage = errorMessage == null || errorMessage.length() <= MAX_ERROR_LENGTH
				? errorMessage
				: errorMessage.substring(0, MAX_ERROR_LENGTH);
		this.processedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getClientId() {
		return clientId;
	}

	public String getFileName() {
		return fileName;
	}

	public DocumentType getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public DocumentStatus getStatus() {
		return status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Integer getChunkCount() {
		return chunkCount;
	}

	public String getEmbeddingModel() {
		return embeddingModel;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

}

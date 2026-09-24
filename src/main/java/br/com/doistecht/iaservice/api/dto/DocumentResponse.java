package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.document.Document;
import br.com.doistecht.iaservice.document.DocumentStatus;
import br.com.doistecht.iaservice.document.DocumentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentResponse(
		Long id,
		String fileName,
		DocumentType contentType,
		long sizeBytes,
		@Schema(description = "PROCESSING enquanto os embeddings são gerados; READY quando pode ser consultado")
		DocumentStatus status,
		@Schema(description = "Motivo da falha, quando o status é FAILED")
		String errorMessage,
		Integer chunkCount,
		String embeddingModel,
		Instant createdAt,
		Instant processedAt) {

	public static DocumentResponse from(Document document) {
		return new DocumentResponse(document.getId(), document.getFileName(), document.getContentType(),
				document.getSizeBytes(), document.getStatus(), document.getErrorMessage(), document.getChunkCount(),
				document.getEmbeddingModel(), document.getCreatedAt(), document.getProcessedAt());
	}

}

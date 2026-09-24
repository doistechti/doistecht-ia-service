package br.com.doistecht.iaservice.document;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conclui o processamento de um documento. Os trechos e o status {@code READY} são
 * gravados na mesma transação: um documento nunca aparece pronto com trechos faltando.
 */
@Service
class DocumentIndexer {

	private final DocumentRepository repository;

	private final DocumentChunkStore chunkStore;

	DocumentIndexer(DocumentRepository repository, DocumentChunkStore chunkStore) {
		this.repository = repository;
		this.chunkStore = chunkStore;
	}

	@Transactional
	public void complete(Long documentId, List<String> chunks, List<float[]> embeddings, String embeddingModel) {
		// Documento apagado durante o processamento: não há o que gravar
		repository.findById(documentId).ifPresent(document -> {
			chunkStore.insertAll(documentId, document.getClientId(), chunks, embeddings);
			document.markReady(chunks.size(), embeddingModel);
		});
	}

	@Transactional
	public void fail(Long documentId, String errorMessage) {
		repository.findById(documentId).ifPresent(document -> document.markFailed(errorMessage));
	}

}

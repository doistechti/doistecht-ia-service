package br.com.doistecht.iaservice.document;

import br.com.doistecht.iaservice.client.AuthenticatedClient;
import br.com.doistecht.iaservice.config.IaServiceProperties;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

@Service
public class DocumentService {

	private static final int MAX_FILE_NAME_LENGTH = 255;

	private final DocumentRepository repository;

	private final DocumentIngestionWorker worker;

	private final DataSize maxDocumentSize;

	public DocumentService(DocumentRepository repository, DocumentIngestionWorker worker,
			IaServiceProperties properties) {
		this.repository = repository;
		this.worker = worker;
		this.maxDocumentSize = properties.rag().maxDocumentSize();
	}

	/**
	 * Registra o documento e inicia o processamento em segundo plano. O documento volta
	 * com status {@code PROCESSING}; o cliente consulta o status até ficar {@code READY}.
	 */
	public Document upload(AuthenticatedClient client, String fileName, byte[] content) {
		if (content == null || content.length == 0) {
			throw new InvalidDocumentException("O arquivo está vazio.");
		}
		if (content.length > maxDocumentSize.toBytes()) {
			throw new InvalidDocumentException("O arquivo excede o tamanho máximo de %d MB."
					.formatted(maxDocumentSize.toMegabytes()));
		}
		DocumentType type = DocumentType.fromFileName(fileName)
				.orElseThrow(() -> new InvalidDocumentException("Formato não suportado. Envie PDF, TXT ou MD."));

		Document document = repository.save(new Document(client.id(), sanitize(fileName), type, content.length));
		worker.process(document.getId(), new DocumentIngestionWorker.Owner(client.id(), client.name()), content, type);
		return document;
	}

	@Transactional(readOnly = true)
	public List<Document> list(Long clientId) {
		return repository.findByClientIdOrderByCreatedAtDesc(clientId);
	}

	@Transactional(readOnly = true)
	public Document get(Long clientId, Long documentId) {
		return repository.findByIdAndClientId(documentId, clientId)
				.orElseThrow(() -> new DocumentNotFoundException(documentId));
	}

	/** Apaga o documento e seus trechos (a exclusão dos trechos é feita pelo banco, em cascata). */
	@Transactional
	public void delete(Long clientId, Long documentId) {
		repository.delete(get(clientId, documentId));
	}

	// Guarda só o nome do arquivo, sem caminhos enviados pelo navegador
	private static String sanitize(String fileName) {
		String name = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1).strip();
		return name.length() > MAX_FILE_NAME_LENGTH ? name.substring(name.length() - MAX_FILE_NAME_LENGTH) : name;
	}

}

package br.com.doistecht.iaservice.document;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.gateway.UsageAttribution;
import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.EmbeddingPurpose;
import br.com.doistecht.iaservice.provider.EmbeddingResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Processa documentos em segundo plano: extrai o texto, divide em trechos, gera os
 * embeddings em lotes e grava tudo no banco. O cliente acompanha pelo status do documento.
 */
@Component
class DocumentIngestionWorker {

	private static final Logger log = LoggerFactory.getLogger(DocumentIngestionWorker.class);

	static final String ENDPOINT = "/v1/documents";

	private final TextExtractor extractor;

	private final TextChunker chunker;

	private final AiProvider aiProvider;

	private final DocumentIndexer indexer;

	private final IaServiceProperties.Rag settings;

	private final int expectedDimensions;

	DocumentIngestionWorker(TextExtractor extractor, TextChunker chunker, AiProvider aiProvider,
			DocumentIndexer indexer, IaServiceProperties properties) {
		this.extractor = extractor;
		this.chunker = chunker;
		this.aiProvider = aiProvider;
		this.indexer = indexer;
		this.settings = properties.rag();
		this.expectedDimensions = properties.rag().embeddingDimensions();
	}

	/** Informações do cliente dono do documento, para contabilizar o uso de embeddings. */
	record Owner(Long clientId, String clientName) {
	}

	@Async
	public void process(Long documentId, Owner owner, byte[] content, DocumentType type) {
		UsageAttribution.runAs(owner.clientId(), owner.clientName(), ENDPOINT, () -> {
			try {
				ingest(documentId, content, type);
			}
			catch (InvalidDocumentException ex) {
				indexer.fail(documentId, ex.getMessage());
			}
			catch (AiProviderException ex) {
				log.warn("Falha ao gerar embeddings do documento {}", documentId, ex);
				// Só vale sugerir nova tentativa quando o provedor está temporariamente fora
				indexer.fail(documentId, ex.getReason() == AiProviderException.Reason.UNAVAILABLE
						? "O provedor de IA está indisponível no momento. Envie o documento novamente em alguns instantes."
						: "O provedor de IA recusou a geração de embeddings. Verifique a configuração do serviço.");
			}
			catch (RuntimeException ex) {
				log.error("Falha inesperada ao processar o documento {}", documentId, ex);
				indexer.fail(documentId, "Erro inesperado ao processar o documento.");
			}
		});
	}

	private void ingest(Long documentId, byte[] content, DocumentType type) {
		String text = extractor.extract(content, type);
		if (text.isBlank()) {
			throw new InvalidDocumentException(
					"Nenhum texto encontrado no documento. PDFs escaneados (só imagens) não são suportados.");
		}

		List<String> chunks = chunker.split(text);
		if (chunks.size() > settings.maxChunks()) {
			throw new InvalidDocumentException("Documento grande demais: %d trechos (máximo de %d)."
					.formatted(chunks.size(), settings.maxChunks()));
		}

		List<float[]> embeddings = new ArrayList<>(chunks.size());
		String model = null;
		for (int start = 0; start < chunks.size(); start += settings.embeddingBatchSize()) {
			if (start > 0) {
				pause(settings.embeddingBatchDelay());
			}
			List<String> batch = chunks.subList(start, Math.min(start + settings.embeddingBatchSize(), chunks.size()));
			EmbeddingResult result = aiProvider.embed(batch, EmbeddingPurpose.DOCUMENT, null);
			if (result.dimensions() != expectedDimensions) {
				throw new IllegalStateException("Embedding com %d dimensões; o banco espera %d"
						.formatted(result.dimensions(), expectedDimensions));
			}
			embeddings.addAll(result.vectors());
			model = result.model();
		}

		indexer.complete(documentId, chunks, embeddings, model);
		log.info("Documento {} processado: {} trechos", documentId, chunks.size());
	}

	private static void pause(Duration delay) {
		if (delay.isZero() || delay.isNegative()) {
			return;
		}
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Processamento interrompido", ex);
		}
	}

}

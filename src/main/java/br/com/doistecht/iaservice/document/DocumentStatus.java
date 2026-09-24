package br.com.doistecht.iaservice.document;

public enum DocumentStatus {

	/** Recebido; extração de texto e geração de embeddings em andamento. */
	PROCESSING,

	/** Pronto para ser consultado. */
	READY,

	/** O processamento falhou; o motivo fica em {@code errorMessage}. */
	FAILED

}

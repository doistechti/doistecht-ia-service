package br.com.doistecht.iaservice.document;

/**
 * O arquivo enviado não pode ser aceito ou processado (formato, tamanho ou conteúdo).
 */
public class InvalidDocumentException extends RuntimeException {

	public InvalidDocumentException(String message) {
		super(message);
	}

	public InvalidDocumentException(String message, Throwable cause) {
		super(message, cause);
	}

}

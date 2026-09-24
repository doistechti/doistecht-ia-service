package br.com.doistecht.iaservice.document;

public class DocumentNotFoundException extends RuntimeException {

	public DocumentNotFoundException(Long id) {
		super("Documento %d não encontrado.".formatted(id));
	}

}

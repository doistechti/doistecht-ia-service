package br.com.doistecht.iaservice.document;

import java.util.Locale;
import java.util.Optional;

/** Formatos de documento aceitos, identificados pela extensão do arquivo. */
public enum DocumentType {

	PDF(".pdf"), TEXT(".txt"), MARKDOWN(".md");

	private final String extension;

	DocumentType(String extension) {
		this.extension = extension;
	}

	public static Optional<DocumentType> fromFileName(String fileName) {
		if (fileName == null) {
			return Optional.empty();
		}
		String lower = fileName.toLowerCase(Locale.ROOT);
		for (DocumentType type : values()) {
			if (lower.endsWith(type.extension)) {
				return Optional.of(type);
			}
		}
		return Optional.empty();
	}

}

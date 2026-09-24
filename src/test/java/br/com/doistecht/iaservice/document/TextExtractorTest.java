package br.com.doistecht.iaservice.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TextExtractorTest {

	private final TextExtractor extractor = new TextExtractor();

	@Test
	void shouldReadUtf8TextAndRemoveByteOrderMark() {
		byte[] content = "﻿Reembolso em até 7 dias úteis.".getBytes(StandardCharsets.UTF_8);

		assertThat(extractor.extract(content, DocumentType.TEXT)).isEqualTo("Reembolso em até 7 dias úteis.");
	}

	@Test
	void shouldExtractTextFromPdf() throws Exception {
		byte[] pdf = TestPdfs.withText("Politica de reembolso: prazo de 7 dias uteis.");

		assertThat(extractor.extract(pdf, DocumentType.PDF)).contains("prazo de 7 dias uteis");
	}

	@Test
	void shouldRejectInvalidPdf() {
		assertThatThrownBy(() -> extractor.extract("não é um PDF".getBytes(StandardCharsets.UTF_8), DocumentType.PDF))
				.isInstanceOf(InvalidDocumentException.class);
	}

	@Test
	void shouldDetectTypeByExtension() {
		assertThat(DocumentType.fromFileName("Manual.PDF")).contains(DocumentType.PDF);
		assertThat(DocumentType.fromFileName("notas.md")).contains(DocumentType.MARKDOWN);
		assertThat(DocumentType.fromFileName("planilha.xlsx")).isEmpty();
		assertThat(DocumentType.fromFileName(null)).isEmpty();
	}

}

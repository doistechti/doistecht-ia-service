package br.com.doistecht.iaservice.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Extrai o texto dos documentos enviados. PDFs são lidos com o Apache PDFBox; apenas
 * PDFs com texto são suportados (PDFs escaneados, só com imagens, precisariam de OCR).
 */
@Component
public class TextExtractor {

	private static final char BYTE_ORDER_MARK = '﻿';

	public String extract(byte[] content, DocumentType type) {
		return switch (type) {
			case PDF -> extractPdf(content);
			case TEXT, MARKDOWN -> stripByteOrderMark(new String(content, StandardCharsets.UTF_8));
		};
	}

	private static String extractPdf(byte[] content) {
		try (PDDocument pdf = Loader.loadPDF(content)) {
			return new PDFTextStripper().getText(pdf);
		}
		catch (IOException ex) {
			throw new InvalidDocumentException("Não foi possível ler o PDF: arquivo inválido ou protegido por senha.", ex);
		}
	}

	private static String stripByteOrderMark(String text) {
		return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
	}

}

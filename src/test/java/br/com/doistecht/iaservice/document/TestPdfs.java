package br.com.doistecht.iaservice.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Gera PDFs simples em memória para os testes. */
public final class TestPdfs {

	private TestPdfs() {
	}

	/** PDF de uma página com o texto informado (apenas caracteres ASCII, pela fonte padrão). */
	public static byte[] withText(String text) throws IOException {
		try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			pdf.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				content.newLineAtOffset(50, 700);
				content.showText(text);
				content.endText();
			}
			pdf.save(out);
			return out.toByteArray();
		}
	}

}

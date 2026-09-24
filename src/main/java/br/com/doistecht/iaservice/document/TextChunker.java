package br.com.doistecht.iaservice.document;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Divide textos em trechos (chunks) para indexação.
 * <p>
 * Cada trecho tem até {@code chunkSize} caracteres e repete os últimos {@code overlap}
 * caracteres do anterior, para que uma informação na fronteira entre dois trechos não se
 * perca. O corte procura, nesta ordem, um fim de parágrafo, um fim de frase ou um espaço,
 * evitando partir palavras e frases ao meio.
 */
@Component
public class TextChunker {

	private static final String[] SENTENCE_ENDINGS = { ". ", "! ", "? ", ".\n", "!\n", "?\n" };

	private final int chunkSize;

	private final int overlap;

	@Autowired
	public TextChunker(IaServiceProperties properties) {
		this(properties.rag().chunkSize(), properties.rag().chunkOverlap());
	}

	TextChunker(int chunkSize, int overlap) {
		if (overlap >= chunkSize) {
			throw new IllegalArgumentException("A sobreposição deve ser menor que o tamanho do trecho");
		}
		this.chunkSize = chunkSize;
		this.overlap = overlap;
	}

	public List<String> split(String text) {
		String normalized = normalize(text);
		List<String> chunks = new ArrayList<>();
		int start = 0;
		while (start < normalized.length()) {
			int end = Math.min(start + chunkSize, normalized.length());
			if (end < normalized.length()) {
				end = breakPoint(normalized, start, end);
			}
			String chunk = normalized.substring(start, end).strip();
			if (!chunk.isEmpty()) {
				chunks.add(chunk);
			}
			if (end >= normalized.length()) {
				break;
			}
			start = nextStart(normalized, start, end);
		}
		return chunks;
	}

	private static String normalize(String text) {
		return text.replace("\r\n", "\n")
				.replace('\r', '\n')
				.replaceAll("[ \\t\\x0B\\f]+", " ")
				.replaceAll(" *\\n *", "\n")
				.replaceAll("\\n{3,}", "\n\n")
				.strip();
	}

	// Procura o melhor ponto de corte na segunda metade do trecho
	private int breakPoint(String text, int start, int end) {
		int minimum = start + chunkSize / 2;
		int paragraph = text.lastIndexOf("\n\n", end);
		if (paragraph >= minimum) {
			return paragraph;
		}
		int sentence = -1;
		for (String ending : SENTENCE_ENDINGS) {
			sentence = Math.max(sentence, text.lastIndexOf(ending, end - ending.length()));
		}
		if (sentence >= minimum) {
			return sentence + 1;
		}
		int space = text.lastIndexOf(' ', end);
		return space >= minimum ? space : end;
	}

	// O próximo trecho começa "overlap" caracteres antes do fim, no início de uma palavra
	private int nextStart(String text, int start, int end) {
		int next = Math.max(end - overlap, start + 1);
		while (next < end && !Character.isWhitespace(text.charAt(next - 1))) {
			next++;
		}
		return next;
	}

}

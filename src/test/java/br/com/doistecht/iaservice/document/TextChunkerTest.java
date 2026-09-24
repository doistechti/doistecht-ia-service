package br.com.doistecht.iaservice.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

	private final TextChunker chunker = new TextChunker(100, 20);

	@Test
	void shouldKeepShortTextInSingleChunk() {
		assertThat(chunker.split("  Um texto curto.\r\n\r\n\r\n\r\nOutro parágrafo.  "))
				.containsExactly("Um texto curto.\n\nOutro parágrafo.");
	}

	@Test
	void shouldSplitLongTextRespectingMaximumSize() {
		String text = "Esta é uma frase de exemplo com algumas palavras. ".repeat(20);

		List<String> chunks = chunker.split(text);

		assertThat(chunks).hasSizeGreaterThan(5).allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(100));
	}

	@Test
	void shouldPreferSentenceBoundaries() {
		String text = "Primeira frase com várias palavras para ocupar espaço no trecho. "
				+ "Segunda frase também longa o bastante para passar do limite de cem caracteres.";

		List<String> chunks = chunker.split(text);

		assertThat(chunks.getFirst()).endsWith("trecho.");
	}

	@Test
	void shouldRepeatEndOfPreviousChunkAtStartOfNext() {
		String text = "alfa beta gama delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma "
				+ "tau upsilon phi chi psi omega um dois tres quatro cinco seis sete oito nove dez";

		List<String> chunks = chunker.split(text);

		assertThat(chunks).hasSizeGreaterThan(1);
		String lastWordOfFirst = chunks.get(0).substring(chunks.get(0).lastIndexOf(' ') + 1);
		assertThat(chunks.get(1)).contains(lastWordOfFirst);
	}

	@Test
	void shouldNotCutWordsAtChunkStart() {
		String text = "palavra ".repeat(60);

		assertThat(chunker.split(text)).allSatisfy(chunk -> assertThat(chunk).startsWith("palavra"));
	}

	@Test
	void shouldHandleTextWithoutSpaces() {
		String text = "x".repeat(250);

		List<String> chunks = chunker.split(text);

		assertThat(String.join("", chunks)).contains("x".repeat(100));
		assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(100));
	}

	@Test
	void shouldReturnNoChunksForBlankText() {
		assertThat(chunker.split(" \n\t ")).isEmpty();
	}

	@Test
	void shouldRejectOverlapNotSmallerThanChunkSize() {
		assertThatThrownBy(() -> new TextChunker(100, 100)).isInstanceOf(IllegalArgumentException.class);
	}

}

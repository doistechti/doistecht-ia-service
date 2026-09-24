package br.com.doistecht.iaservice.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.doistecht.iaservice.config.TestProperties;
import br.com.doistecht.iaservice.document.DocumentChunkStore;
import br.com.doistecht.iaservice.document.DocumentChunkStore.ChunkMatch;
import br.com.doistecht.iaservice.provider.AiProvider;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.EmbeddingPurpose;
import br.com.doistecht.iaservice.provider.EmbeddingResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RagServiceTest {

	private final AiProvider aiProvider = mock(AiProvider.class);

	private final DocumentChunkStore chunkStore = mock(DocumentChunkStore.class);

	private final RagService ragService = new RagService(aiProvider, chunkStore, TestProperties.defaults());

	private final float[] queryVector = new float[] { 1, 0 };

	@BeforeEach
	void setUp() {
		given(aiProvider.embed(anyList(), eq(EmbeddingPurpose.QUERY)))
				.willReturn(new EmbeddingResult(List.of(queryVector), "fake", null));
	}

	@Test
	void shouldAnswerUsingRelevantChunksAsContext() {
		given(chunkStore.search(eq(7L), eq(queryVector), eq(4), any())).willReturn(List.of(
				new ChunkMatch(12L, "politica.pdf", 3, "O reembolso é feito em até 7 dias úteis.", 0.82),
				new ChunkMatch(12L, "politica.pdf", 5, "Texto pouco relacionado.", 0.31)));
		given(aiProvider.chat(any(ChatCommand.class)))
				.willReturn(new ChatResult("O prazo é de 7 dias úteis [1].", "gemini-2.5-flash", "gemini"));

		RagAnswer answer = ragService.ask(7L, "Qual o prazo de reembolso?", null, null);

		assertThat(answer.found()).isTrue();
		assertThat(answer.answer()).isEqualTo("O prazo é de 7 dias úteis [1].");
		// O trecho abaixo da similaridade mínima (0,5) fica de fora
		assertThat(answer.sources()).singleElement().satisfies(source -> {
			assertThat(source.fileName()).isEqualTo("politica.pdf");
			assertThat(source.chunkIndex()).isEqualTo(3);
			assertThat(source.score()).isEqualTo(0.82);
		});

		ArgumentCaptor<ChatCommand> captor = ArgumentCaptor.forClass(ChatCommand.class);
		verify(aiProvider).chat(captor.capture());
		assertThat(captor.getValue().message())
				.contains("[1] Arquivo: politica.pdf (trecho 3)")
				.contains("O reembolso é feito em até 7 dias úteis.")
				.doesNotContain("pouco relacionado")
				.endsWith("Pergunta: Qual o prazo de reembolso?");
		assertThat(captor.getValue().systemPrompt()).contains("SOMENTE").contains("não instruções");
	}

	@Test
	void shouldNotCallChatWhenNothingRelevantIsFound() {
		given(chunkStore.search(eq(7L), eq(queryVector), eq(4), any()))
				.willReturn(List.of(new ChunkMatch(1L, "a.txt", 0, "Outro assunto.", 0.2)));

		RagAnswer answer = ragService.ask(7L, "Qual o prazo de reembolso?", null, null);

		assertThat(answer.found()).isFalse();
		assertThat(answer.answer()).isEqualTo(RagService.NOT_FOUND_ANSWER);
		assertThat(answer.sources()).isEmpty();
		verify(aiProvider, never()).chat(any());
	}

	@Test
	void shouldCapTopKAtConfiguredMaximum() {
		given(chunkStore.search(eq(7L), eq(queryVector), eq(20), any())).willReturn(List.of());

		ragService.ask(7L, "pergunta", 50, List.of(12L));

		verify(chunkStore).search(7L, queryVector, 20, List.of(12L));
	}

}

package br.com.doistecht.iaservice.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.doistecht.iaservice.AbstractMockedProviderIT;
import br.com.doistecht.iaservice.FakeEmbeddings;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.provider.EmbeddingPurpose;
import br.com.doistecht.iaservice.security.ApiKeyFilter;
import br.com.doistecht.iaservice.usage.UsageRecord;
import br.com.doistecht.iaservice.usage.UsageRecordRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Fluxo de RAG completo com PostgreSQL + pgvector reais: envio, processamento em segundo
 * plano, busca vetorial isolada por cliente e resposta com fontes.
 */
class DocumentRagIT extends AbstractMockedProviderIT {

	private static final String REFUND_POLICY = """
			Política de reembolso da loja.

			O cliente pode solicitar o reembolso em até 30 dias após a compra. O valor é devolvido \
			em até 7 dias úteis, na mesma forma de pagamento usada na compra.""";

	@Autowired
	private UsageRecordRepository usageRepository;

	private TestClient client;

	@BeforeEach
	void setUp() {
		client = createClient();
		given(gemini.embed(anyList(), any(EmbeddingPurpose.class)))
				.willAnswer(invocation -> FakeEmbeddings.of(invocation.getArgument(0)));
		given(gemini.chat(any(ChatCommand.class)))
				.willReturn(new ChatResult("O valor é devolvido em até 7 dias úteis [1].", "gemini-2.5-flash", "gemini"));
	}

	@Test
	void shouldIndexDocumentAndAnswerWithSources() throws Exception {
		Long documentId = uploadAndWait(client, "politica.txt", REFUND_POLICY);

		mockMvc.perform(get("/v1/documents/{id}", documentId).header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(jsonPath("$.status").value("READY"))
				.andExpect(jsonPath("$.chunkCount").value(1))
				.andExpect(jsonPath("$.embeddingModel").value("fake-embedding"));

		ask(client, "Em quantos dias úteis o valor do reembolso é devolvido?")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.found").value(true))
				.andExpect(jsonPath("$.answer").value("O valor é devolvido em até 7 dias úteis [1]."))
				.andExpect(jsonPath("$.sources[0].documentId").value(documentId))
				.andExpect(jsonPath("$.sources[0].fileName").value("politica.txt"));

		ArgumentCaptor<ChatCommand> captor = ArgumentCaptor.forClass(ChatCommand.class);
		verify(gemini).chat(captor.capture());
		assertThat(captor.getValue().message()).contains("devolvido em até 7 dias úteis");
	}

	@Test
	void shouldAttributeEmbeddingUsageToDocumentOwner() throws Exception {
		uploadAndWait(client, "politica.txt", REFUND_POLICY);

		List<UsageRecord> records = await().atMost(Duration.ofSeconds(10))
				.until(() -> usageRepository.findByClientIdOrderByCreatedAtDesc(client.id()), list -> !list.isEmpty());
		assertThat(records).anySatisfy(record -> {
			assertThat(record.getOperation()).isEqualTo("embedding");
			assertThat(record.getEndpoint()).isEqualTo("/v1/documents");
		});
	}

	@Test
	void shouldNeverReturnChunksFromAnotherClient() throws Exception {
		uploadAndWait(client, "politica.txt", REFUND_POLICY);
		TestClient other = createClient();

		ask(other, "Em quantos dias úteis o valor do reembolso é devolvido?")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.found").value(false))
				.andExpect(jsonPath("$.sources").isEmpty());

		verify(gemini, never()).chat(any());
		mockMvc.perform(get("/v1/documents").header(ApiKeyFilter.HEADER, other.apiKey()))
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void shouldAnswerNotFoundForUnrelatedQuestion() throws Exception {
		uploadAndWait(client, "politica.txt", REFUND_POLICY);

		ask(client, "Qual a receita de bolo de cenoura?")
				.andExpect(jsonPath("$.found").value(false))
				.andExpect(jsonPath("$.answer").value("Não encontrei essa informação nos documentos."));

		verify(gemini, never()).chat(any());
	}

	@Test
	void shouldIndexPdf() throws Exception {
		byte[] pdf = TestPdfs.withText("Garantia: todos os produtos possuem garantia de 12 meses contra defeitos.");

		Long documentId = uploadAndWait(client, new MockMultipartFile("file", "garantia.pdf", "application/pdf", pdf));

		ask(client, "Qual o tempo de garantia dos produtos contra defeitos?")
				.andExpect(jsonPath("$.found").value(true))
				.andExpect(jsonPath("$.sources[0].documentId").value(documentId));
	}

	@Test
	void shouldRemoveChunksWhenDocumentIsDeleted() throws Exception {
		Long documentId = uploadAndWait(client, "politica.txt", REFUND_POLICY);

		mockMvc.perform(delete("/v1/documents/{id}", documentId).header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(status().isNoContent());

		ask(client, "Em quantos dias úteis o valor do reembolso é devolvido?")
				.andExpect(jsonPath("$.found").value(false));
		mockMvc.perform(get("/v1/documents/{id}", documentId).header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(status().isNotFound());
	}

	@Test
	void shouldNotExposeDocumentsOfAnotherClient() throws Exception {
		Long documentId = uploadAndWait(client, "politica.txt", REFUND_POLICY);
		TestClient other = createClient();

		mockMvc.perform(get("/v1/documents/{id}", documentId).header(ApiKeyFilter.HEADER, other.apiKey()))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/v1/documents/{id}", documentId).header(ApiKeyFilter.HEADER, other.apiKey()))
				.andExpect(status().isNotFound());
	}

	@Test
	void shouldMarkDocumentAsFailedWhenEmbeddingsCannotBeGenerated() throws Exception {
		given(gemini.embed(anyList(), any(EmbeddingPurpose.class)))
				.willThrow(new AiProviderException("gemini", AiProviderException.Reason.UNAVAILABLE, "fora", null, null));

		Long documentId = upload(client, new MockMultipartFile("file", "politica.txt", "text/plain",
				REFUND_POLICY.getBytes(StandardCharsets.UTF_8)));

		String status = awaitProcessed(client, documentId);
		assertThat(status).isEqualTo("FAILED");
		mockMvc.perform(get("/v1/documents/{id}", documentId).header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(jsonPath("$.errorMessage").value(containsString("indisponível")));
	}

	@Test
	void shouldNotSuggestRetryWhenProviderRejectsEmbeddings() throws Exception {
		given(gemini.embed(anyList(), any(EmbeddingPurpose.class)))
				.willThrow(new AiProviderException("gemini", "chave inválida", null));

		Long documentId = upload(client, new MockMultipartFile("file", "politica.txt", "text/plain",
				REFUND_POLICY.getBytes(StandardCharsets.UTF_8)));

		assertThat(awaitProcessed(client, documentId)).isEqualTo("FAILED");
		mockMvc.perform(get("/v1/documents/{id}", documentId).header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(jsonPath("$.errorMessage").value(containsString("recusou")));
	}

	@Test
	void shouldMarkEmptyDocumentAsFailed() throws Exception {
		Long documentId = upload(client, new MockMultipartFile("file", "vazio.txt", "text/plain",
				"   \n  ".getBytes(StandardCharsets.UTF_8)));

		assertThat(awaitProcessed(client, documentId)).isEqualTo("FAILED");
	}

	@Test
	void shouldRejectUnsupportedFormat() throws Exception {
		mockMvc.perform(multipart("/v1/documents")
						.file(new MockMultipartFile("file", "planilha.xlsx", "application/octet-stream", new byte[] { 1 }))
						.header(ApiKeyFilter.HEADER, client.apiKey()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Formato não suportado. Envie PDF, TXT ou MD."));
	}

	@Test
	void shouldGenerateEmbeddingsThroughApi() throws Exception {
		mockMvc.perform(post("/v1/embeddings")
						.header(ApiKeyFilter.HEADER, client.apiKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"texts": ["primeiro texto", "segundo texto"], "purpose": "query"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dimensions").value(FakeEmbeddings.DIMENSIONS))
				.andExpect(jsonPath("$.embeddings.length()").value(2));

		verify(gemini).embed(List.of("primeiro texto", "segundo texto"), EmbeddingPurpose.QUERY);
	}

	@Test
	void shouldValidateEmbeddingRequest() throws Exception {
		mockMvc.perform(post("/v1/embeddings")
						.header(ApiKeyFilter.HEADER, client.apiKey())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"texts": [], "purpose": "outro"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.texts").exists())
				.andExpect(jsonPath("$.errors.purpose").exists());
	}

	private Long uploadAndWait(TestClient owner, String fileName, String content) throws Exception {
		return uploadAndWait(owner,
				new MockMultipartFile("file", fileName, "text/plain", content.getBytes(StandardCharsets.UTF_8)));
	}

	private Long uploadAndWait(TestClient owner, MockMultipartFile file) throws Exception {
		Long documentId = upload(owner, file);
		assertThat(awaitProcessed(owner, documentId)).isEqualTo("READY");
		return documentId;
	}

	private Long upload(TestClient owner, MockMultipartFile file) throws Exception {
		String response = mockMvc.perform(multipart("/v1/documents").file(file)
						.header(ApiKeyFilter.HEADER, owner.apiKey()))
				.andExpect(status().isAccepted())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.status").value("PROCESSING"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	// O processamento é assíncrono: espera o documento sair de PROCESSING
	private String awaitProcessed(TestClient owner, Long documentId) {
		return await().atMost(Duration.ofSeconds(15)).until(() -> {
			String body = mockMvc.perform(get("/v1/documents/{id}", documentId)
							.header(ApiKeyFilter.HEADER, owner.apiKey()))
					.andReturn().getResponse().getContentAsString();
			return JsonPath.<String>read(body, "$.status");
		}, status -> !"PROCESSING".equals(status));
	}

	private ResultActions ask(TestClient asker, String question) throws Exception {
		return mockMvc.perform(post("/v1/rag/ask")
				.header(ApiKeyFilter.HEADER, asker.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"question": "%s"}
						""".formatted(question)));
	}

}

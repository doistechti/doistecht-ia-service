package br.com.doistecht.iaservice.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava e busca trechos de documentos no PostgreSQL com pgvector, em SQL puro.
 * <p>
 * O isolamento entre clientes é feito por uma coluna ({@code client_id}) presente em
 * toda busca, e não por um filtro opcional de metadados: não há como uma consulta
 * esquecer o filtro e devolver trechos de outro cliente.
 */
@Repository
public class DocumentChunkStore {

	private final JdbcTemplate jdbc;

	public DocumentChunkStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Trecho encontrado na busca, com a similaridade de cosseno (0 a 1) em relação à pergunta. */
	public record ChunkMatch(Long documentId, String fileName, int chunkIndex, String content, double score) {
	}

	public void insertAll(Long documentId, Long clientId, List<String> chunks, List<float[]> embeddings) {
		List<Object[]> rows = new ArrayList<>(chunks.size());
		for (int i = 0; i < chunks.size(); i++) {
			rows.add(new Object[] { documentId, clientId, i, chunks.get(i), toVectorLiteral(embeddings.get(i)) });
		}
		jdbc.batchUpdate("""
				INSERT INTO document_chunk (document_id, client_id, chunk_index, content, embedding)
				VALUES (?, ?, ?, ?, ?::vector)
				""", rows);
	}

	/**
	 * Busca os trechos mais parecidos com o vetor informado, entre os documentos prontos do cliente.
	 *
	 * @param embeddingModel só compara com trechos indexados pelo mesmo modelo: vetores de modelos
	 *                       diferentes não são comparáveis, mesmo tendo a mesma dimensão
	 * @param documentIds    restringe a busca a estes documentos; vazio busca em todos
	 */
	@Transactional(readOnly = true)
	public List<ChunkMatch> search(Long clientId, float[] query, String embeddingModel, int topK,
			List<Long> documentIds) {
		// Com filtro por cliente, o índice HNSW sozinho pode devolver menos de topK trechos;
		// a busca iterativa do pgvector 0.8 continua procurando até completar o resultado
		jdbc.execute("SET LOCAL hnsw.iterative_scan = relaxed_order");

		String vector = toVectorLiteral(query);
		List<Object> params = new ArrayList<>(List.of(vector, clientId, embeddingModel));
		String documentFilter = "";
		if (documentIds != null && !documentIds.isEmpty()) {
			documentFilter = " AND c.document_id IN (" + String.join(", ", Collections.nCopies(documentIds.size(), "?"))
					+ ")";
			params.addAll(documentIds);
		}
		params.add(vector);
		params.add(topK);

		// <=> é a distância de cosseno do pgvector; similaridade = 1 - distância
		String sql = """
				SELECT c.document_id, d.file_name, c.chunk_index, c.content,
				       1 - (c.embedding <=> ?::vector) AS score
				FROM document_chunk c
				JOIN document d ON d.id = c.document_id
				WHERE c.client_id = ? AND d.embedding_model = ? AND d.status = 'READY'%s
				ORDER BY c.embedding <=> ?::vector
				LIMIT ?
				""".formatted(documentFilter);

		return jdbc.query(sql, (rs, rowNum) -> new ChunkMatch(rs.getLong("document_id"), rs.getString("file_name"),
				rs.getInt("chunk_index"), rs.getString("content"), rs.getDouble("score")), params.toArray());
	}

	static String toVectorLiteral(float[] vector) {
		StringBuilder literal = new StringBuilder(vector.length * 12).append('[');
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				literal.append(',');
			}
			literal.append(vector[i]);
		}
		return literal.append(']').toString();
	}

}

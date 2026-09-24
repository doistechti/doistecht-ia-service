package br.com.doistecht.iaservice.provider;

import java.util.List;
import reactor.core.publisher.Flux;

/**
 * Contrato comum a todos os provedores de IA.
 * <p>
 * Nenhuma classe fora do pacote {@code provider} deve depender do Spring AI
 * ou de um provedor específico: elas conversam apenas com esta interface.
 */
public interface AiProvider {

	/** Identificador do provedor, ex.: {@code gemini}. */
	String name();

	ChatResult chat(ChatCommand command);

	/** Resposta entregue em partes, à medida que o modelo gera o texto. */
	Flux<StreamChunk> chatStream(ChatCommand command);

	/**
	 * Pede ao modelo uma resposta em JSON que siga o schema informado.
	 * O conteúdo retornado é o JSON em texto, ainda não validado.
	 */
	ChatResult structured(ChatCommand command, String jsonSchema);

	/**
	 * Gera um embedding para cada texto, na mesma ordem.
	 * <p>
	 * Todos os vetores têm a mesma dimensão, fixa por configuração: ela precisa bater
	 * com a coluna {@code vector} do banco, então não há fallback para outro modelo.
	 */
	EmbeddingResult embed(List<String> texts, EmbeddingPurpose purpose);

}

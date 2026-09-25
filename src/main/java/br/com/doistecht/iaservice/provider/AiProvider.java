package br.com.doistecht.iaservice.provider;

import java.util.List;
import reactor.core.publisher.Flux;

/**
 * Porta de entrada para os modelos de IA, usada por controllers e serviços.
 * <p>
 * É implementada pelo gateway, que escolhe o {@link ModelProvider} de cada chamada
 * (pelo campo {@link ChatCommand#provider()}, pelo provedor padrão do cliente ou pelo
 * padrão global) e adiciona cache, registro de uso, métricas e fallback entre provedores.
 * Nenhuma classe fora do pacote {@code provider} conhece o Spring AI ou um provedor específico.
 */
public interface AiProvider {

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
	 * Não há fallback entre provedores: embeddings de modelos diferentes não são
	 * comparáveis entre si, e os trechos já indexados ficariam inúteis.
	 *
	 * @param provider provedor a usar; {@code null} usa o provedor de embeddings do RAG
	 */
	EmbeddingResult embed(List<String> texts, EmbeddingPurpose purpose, String provider);

}

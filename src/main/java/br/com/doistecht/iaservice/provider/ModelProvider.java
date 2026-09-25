package br.com.doistecht.iaservice.provider;

import java.util.List;
import reactor.core.publisher.Flux;

/**
 * Contrato de um provedor concreto de IA (Gemini, Ollama...).
 * <p>
 * O restante da aplicação não usa esta interface diretamente: fala com {@link AiProvider},
 * implementada pelo gateway, que escolhe o provedor de cada chamada e adiciona cache,
 * registro de uso, métricas e fallback entre provedores. Para adicionar um provedor novo
 * basta criar um bean que implemente esta interface.
 */
public interface ModelProvider {

	/** Identificador do provedor, ex.: {@code gemini}. Usado para escolher o provedor nas requisições. */
	String name();

	ChatResult chat(ChatCommand command);

	/** Resposta entregue em partes, à medida que o modelo gera o texto. */
	Flux<StreamChunk> chatStream(ChatCommand command);

	/**
	 * Pede ao modelo uma resposta em JSON que siga o schema informado.
	 * O conteúdo retornado é o JSON em texto, ainda não validado.
	 */
	ChatResult structured(ChatCommand command, String jsonSchema);

	/** Gera um embedding normalizado para cada texto, na mesma ordem. */
	EmbeddingResult embed(List<String> texts, EmbeddingPurpose purpose);

	/** Modelos configurados, para exibição em {@code GET /v1/providers}. */
	ProviderInfo info();

	/** Situação atual do provedor. Deve ser rápida: é consultada em health checks. */
	ProviderHealth health();

}

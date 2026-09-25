package br.com.doistecht.iaservice.config;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.util.unit.DataSize;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configurações próprias do serviço (prefixo {@code ia-service}).
 *
 * @param adminKey chave de administrador, exigida nas rotas {@code /v1/admin/**}
 * @param auth     autenticação de clientes
 * @param cache    cache de respostas
 * @param pricing  preço por milhão de tokens de cada modelo, usado para estimar custo
 * @param rag       documentos e busca semântica
 * @param providers escolha do provedor de IA
 */
@Validated
@ConfigurationProperties(prefix = "ia-service")
public record IaServiceProperties(
		@NotBlank String adminKey,
		@DefaultValue Auth auth,
		@DefaultValue Cache cache,
		Map<String, ModelPrice> pricing,
		@DefaultValue Rag rag,
		@DefaultValue Providers providers) {

	public IaServiceProperties {
		pricing = pricing == null ? Map.of() : Map.copyOf(pricing);
	}

	/** @param keyCacheTtl por quanto tempo o resultado da validação de uma chave fica em memória */
	public record Auth(@DefaultValue("30s") Duration keyCacheTtl) {
	}

	public record Cache(@DefaultValue("true") boolean enabled, @DefaultValue("1h") Duration ttl) {
	}

	public record ModelPrice(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
	}

	/**
	 * @param chunkSize           tamanho máximo de cada trecho, em caracteres
	 * @param chunkOverlap        caracteres repetidos entre trechos vizinhos
	 * @param maxDocumentSize     tamanho máximo do arquivo enviado
	 * @param maxChunks           máximo de trechos por documento (limita chamadas de embedding)
	 * @param embeddingBatchSize  trechos por chamada de embedding (o Gemini aceita até 100)
	 * @param embeddingBatchDelay pausa entre lotes, para respeitar o limite por minuto do plano gratuito
	 * @param defaultTopK         trechos usados como contexto quando o cliente não informa
	 * @param maxTopK             máximo de trechos que o cliente pode pedir
	 * @param minScore            similaridade mínima (0 a 1) para um trecho ser considerado relevante
	 * @param embeddingDimensions dimensão dos embeddings; precisa ser igual à coluna {@code vector(768)} do banco
	 * @param embeddingProvider   provedor dos embeddings dos documentos; fixo, pois vetores de modelos diferentes
	 *                            não são comparáveis
	 */
	public record Rag(
			@DefaultValue("1000") int chunkSize,
			@DefaultValue("200") int chunkOverlap,
			@DefaultValue("10MB") DataSize maxDocumentSize,
			@DefaultValue("500") int maxChunks,
			@DefaultValue("50") int embeddingBatchSize,
			@DefaultValue("500ms") Duration embeddingBatchDelay,
			@DefaultValue("4") int defaultTopK,
			@DefaultValue("20") int maxTopK,
			@DefaultValue("0.6") double minScore,
			@DefaultValue("768") int embeddingDimensions,
			@DefaultValue("gemini") String embeddingProvider) {
	}

	/**
	 * @param defaultProvider provedor usado quando nem a requisição nem o cliente escolhem um
	 * @param fallback        provedor reserva quando o escolhido está indisponível; vazio desativa
	 */
	public record Providers(@DefaultValue("gemini") String defaultProvider, String fallback) {
	}

}

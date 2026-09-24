package br.com.doistecht.iaservice.gateway;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.provider.ChatCommand;
import br.com.doistecht.iaservice.provider.ChatResult;
import br.com.doistecht.iaservice.support.Hashes;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Cache de respostas do modelo no Redis, separado por cliente.
 * <p>
 * A chave é o hash de tudo o que influencia a resposta: cliente, operação,
 * provedor, prompts, histórico e schema. Falhas no Redis nunca derrubam a
 * requisição: o cache apenas deixa de ser usado.
 */
@Component
class ResponseCache {

	private static final Logger log = LoggerFactory.getLogger(ResponseCache.class);

	private static final String KEY_PREFIX = "ia:cache:";

	private final StringRedisTemplate redis;

	private final ObjectMapper objectMapper;

	private final boolean enabled;

	private final Duration ttl;

	ResponseCache(StringRedisTemplate redis, ObjectMapper objectMapper, IaServiceProperties properties) {
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.enabled = properties.cache().enabled();
		this.ttl = properties.cache().ttl();
	}

	/** Só o necessário para devolver a resposta; tokens não são guardados porque não são cobrados de novo. */
	record CachedResponse(String content, String model, String provider) {
	}

	String key(Long clientId, String operation, String provider, ChatCommand command, String jsonSchema) {
		Map<String, Object> parts = new LinkedHashMap<>();
		parts.put("client", clientId);
		parts.put("operation", operation);
		parts.put("provider", provider);
		parts.put("system", command.systemPrompt());
		parts.put("history", command.history());
		parts.put("message", command.message());
		parts.put("schema", jsonSchema);
		return KEY_PREFIX + Hashes.sha256Hex(objectMapper.writeValueAsString(parts));
	}

	Optional<ChatResult> get(String key) {
		if (!enabled) {
			return Optional.empty();
		}
		try {
			String json = redis.opsForValue().get(key);
			if (json == null) {
				return Optional.empty();
			}
			CachedResponse cached = objectMapper.readValue(json, CachedResponse.class);
			return Optional.of(new ChatResult(cached.content(), cached.model(), cached.provider()));
		}
		catch (RuntimeException ex) {
			log.warn("Falha ao ler o cache de respostas; seguindo sem cache", ex);
			return Optional.empty();
		}
	}

	void put(String key, ChatResult result) {
		if (!enabled) {
			return;
		}
		try {
			String json = objectMapper.writeValueAsString(
					new CachedResponse(result.content(), result.model(), result.provider()));
			redis.opsForValue().set(key, json, ttl);
		}
		catch (RuntimeException ex) {
			log.warn("Falha ao gravar no cache de respostas", ex);
		}
	}

}

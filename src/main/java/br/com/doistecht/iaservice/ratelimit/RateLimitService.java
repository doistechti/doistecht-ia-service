package br.com.doistecht.iaservice.ratelimit;

import br.com.doistecht.iaservice.client.AuthenticatedClient;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Aplica dois limites por cliente, ambos guardados no Redis (funcionam com várias instâncias):
 * <ul>
 * <li><b>por minuto</b>: token bucket do Bucket4j, que recarrega aos poucos ao longo do minuto;</li>
 * <li><b>cota diária</b>: contador por dia do calendário (UTC), zerado à meia-noite.</li>
 * </ul>
 * Se o Redis estiver fora do ar, os limites deixam de ser aplicados (fail open) para não
 * derrubar o serviço inteiro; o problema fica registrado no log.
 */
@Service
public class RateLimitService {

	private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

	private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(2);

	private final LettuceConnectionFactory connectionFactory;

	private final StringRedisTemplate redis;

	private final Clock clock = Clock.systemUTC();

	private volatile ProxyManager<byte[]> proxyManager;

	public RateLimitService(LettuceConnectionFactory connectionFactory, StringRedisTemplate redis) {
		this.connectionFactory = connectionFactory;
		this.redis = redis;
	}

	/**
	 * Consome uma requisição dos limites do cliente.
	 *
	 * @throws RateLimitExceededException se algum limite foi atingido
	 */
	public RateLimitDecision consume(AuthenticatedClient client) {
		try {
			ConsumptionProbe probe = bucketFor(client).tryConsumeAndReturnRemaining(1);
			if (!probe.isConsumed()) {
				throw new RateLimitExceededException(RateLimitExceededException.Limit.PER_MINUTE,
						toSeconds(probe.getNanosToWaitForRefill()));
			}

			long used = incrementDailyCounter(client);
			if (used > client.dailyQuota()) {
				redis.opsForValue().decrement(dailyKey(client));
				throw new RateLimitExceededException(RateLimitExceededException.Limit.DAILY_QUOTA,
						secondsUntilMidnight());
			}

			return new RateLimitDecision(client.rateLimitPerMinute(), probe.getRemainingTokens(),
					client.dailyQuota(), client.dailyQuota() - used);
		}
		catch (RateLimitExceededException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			log.warn("Não foi possível aplicar o rate limit (Redis indisponível?). Requisição liberada.", ex);
			return RateLimitDecision.UNAVAILABLE;
		}
	}

	private Bucket bucketFor(AuthenticatedClient client) {
		// O limite faz parte da chave: se o admin alterar o limite, um bucket novo é criado
		byte[] key = ("ia:ratelimit:" + client.id() + ":" + client.rateLimitPerMinute())
				.getBytes(StandardCharsets.UTF_8);
		return proxyManager().builder().build(key, () -> BucketConfiguration.builder()
				.addLimit(limit -> limit.capacity(client.rateLimitPerMinute())
						.refillGreedy(client.rateLimitPerMinute(), Duration.ofMinutes(1)))
				.build());
	}

	private long incrementDailyCounter(AuthenticatedClient client) {
		String key = dailyKey(client);
		Long used = redis.opsForValue().increment(key);
		if (used != null && used == 1) {
			redis.expire(key, Duration.ofDays(2));
		}
		return used == null ? 0 : used;
	}

	private String dailyKey(AuthenticatedClient client) {
		return "ia:quota:" + client.id() + ":" + LocalDate.now(clock);
	}

	private long secondsUntilMidnight() {
		Instant midnight = LocalDate.now(clock).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		return Math.max(1, Duration.between(clock.instant(), midnight).toSeconds());
	}

	private static long toSeconds(long nanos) {
		return Math.max(1, Duration.ofNanos(nanos).toSeconds() + 1);
	}

	// Criado sob demanda: o cliente Redis do Spring só fica disponível depois que o contexto sobe
	private ProxyManager<byte[]> proxyManager() {
		ProxyManager<byte[]> manager = proxyManager;
		if (manager == null) {
			synchronized (this) {
				manager = proxyManager;
				if (manager == null) {
					manager = Bucket4jLettuce.casBasedBuilder((RedisClient) connectionFactory.getNativeClient())
							.expirationAfterWrite(
									ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
							.requestTimeout(REDIS_TIMEOUT)
							.build();
					proxyManager = manager;
				}
			}
		}
		return manager;
	}

}

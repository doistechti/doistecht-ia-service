package br.com.doistecht.iaservice.ratelimit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import java.time.Duration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente Redis exclusivo do Bucket4j.
 * <p>
 * O Bucket4j guarda a conexão que recebe. Se ela viesse do {@code LettuceConnectionFactory}
 * do Spring, que fecha e recria o cliente interno ao ser parado e reiniciado, o rate limit
 * ficaria preso a uma conexão fechada e passaria a liberar tudo. Com um cliente próprio,
 * encerrado só no fim da aplicação, a reconexão automática do Lettuce cuida de quedas do Redis.
 */
@Configuration(proxyBeanMethods = false)
class RateLimitRedisConfig {

	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	@Bean(destroyMethod = "shutdown")
	RedisClient rateLimitRedisClient(DataRedisConnectionDetails connectionDetails) {
		var standalone = connectionDetails.getStandalone();
		RedisURI.Builder uri = RedisURI.builder()
				.withHost(standalone.getHost())
				.withPort(standalone.getPort())
				.withDatabase(standalone.getDatabase())
				.withTimeout(TIMEOUT);
		if (connectionDetails.getPassword() != null) {
			if (connectionDetails.getUsername() != null) {
				uri.withAuthentication(connectionDetails.getUsername(), connectionDetails.getPassword());
			}
			else {
				uri.withPassword(connectionDetails.getPassword().toCharArray());
			}
		}
		return RedisClient.create(uri.build());
	}

}

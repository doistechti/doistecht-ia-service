package br.com.doistecht.iaservice.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import br.com.doistecht.iaservice.client.AuthenticatedClient;
import br.com.doistecht.iaservice.metrics.GatewayMetrics;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.codec.RedisCodec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class RateLimitServiceTest {

	@Test
	void shouldAllowRequestWhenRedisIsUnavailable() {
		RedisClient redisClient = mock(RedisClient.class);
		given(redisClient.connect(any(RedisCodec.class))).willThrow(new RedisConnectionException("Redis fora do ar"));
		RateLimitService service = new RateLimitService(redisClient, mock(StringRedisTemplate.class),
				new GatewayMetrics(new SimpleMeterRegistry()));

		RateLimitDecision decision = service.consume(new AuthenticatedClient(1L, "portal", 10, 200));

		assertThat(decision.isAvailable()).isFalse();
	}

}

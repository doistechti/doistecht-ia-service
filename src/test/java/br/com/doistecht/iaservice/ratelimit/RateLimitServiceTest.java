package br.com.doistecht.iaservice.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import br.com.doistecht.iaservice.client.AuthenticatedClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RateLimitServiceTest {

	@Test
	void shouldAllowRequestWhenRedisIsUnavailable() {
		LettuceConnectionFactory connectionFactory = mock(LettuceConnectionFactory.class);
		given(connectionFactory.getNativeClient()).willThrow(new RedisConnectionFailureException("Redis fora do ar"));
		RateLimitService service = new RateLimitService(connectionFactory, mock(StringRedisTemplate.class));

		RateLimitDecision decision = service.consume(new AuthenticatedClient(1L, "portal", 10, 200));

		assertThat(decision.isAvailable()).isFalse();
	}

}

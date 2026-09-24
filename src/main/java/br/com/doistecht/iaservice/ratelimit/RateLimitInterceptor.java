package br.com.doistecht.iaservice.ratelimit;

import br.com.doistecht.iaservice.client.AuthenticatedClient;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Consome os limites do cliente antes de cada chamada às rotas de IA e informa
 * o saldo restante nos headers da resposta.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

	public static final String LIMIT_HEADER = "X-RateLimit-Limit";

	public static final String REMAINING_HEADER = "X-RateLimit-Remaining";

	public static final String QUOTA_LIMIT_HEADER = "X-Quota-Limit";

	public static final String QUOTA_REMAINING_HEADER = "X-Quota-Remaining";

	private final RateLimitService rateLimitService;

	public RateLimitInterceptor(RateLimitService rateLimitService) {
		this.rateLimitService = rateLimitService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// No streaming, a requisição volta ao servlet (dispatch ASYNC) ao terminar: não conta de novo
		if (request.getDispatcherType() == DispatcherType.ASYNC) {
			return true;
		}
		if (!(request.getAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE) instanceof AuthenticatedClient client)) {
			return true;
		}

		RateLimitDecision decision = rateLimitService.consume(client);
		if (decision.isAvailable()) {
			response.setHeader(LIMIT_HEADER, String.valueOf(decision.minuteLimit()));
			response.setHeader(REMAINING_HEADER, String.valueOf(decision.minuteRemaining()));
			response.setHeader(QUOTA_LIMIT_HEADER, String.valueOf(decision.dailyQuota()));
			response.setHeader(QUOTA_REMAINING_HEADER, String.valueOf(decision.dailyRemaining()));
		}
		return true;
	}

}

package br.com.doistecht.iaservice.gateway;

import br.com.doistecht.iaservice.client.AuthenticatedClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Dados da requisição HTTP que originou a chamada ao modelo.
 * <p>
 * Precisa ser capturado na thread da requisição: no streaming, o fim da resposta
 * acontece em outra thread, onde a requisição já não está disponível.
 *
 * @param clientId    cliente autenticado, ou {@code null} fora de uma requisição de cliente
 * @param endpoint    rota chamada
 * @param bypassCache {@code true} quando o cliente enviou {@code Cache-Control: no-cache}
 */
record CallContext(Long clientId, String endpoint, boolean bypassCache) {

	private static final int MAX_ENDPOINT_LENGTH = 200;

	static final CallContext NONE = new CallContext(null, "internal", false);

	static CallContext current() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return NONE;
		}
		HttpServletRequest request = attributes.getRequest();
		Long clientId = request.getAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE) instanceof AuthenticatedClient client
				? client.id()
				: null;
		String endpoint = request.getRequestURI();
		if (endpoint.length() > MAX_ENDPOINT_LENGTH) {
			endpoint = endpoint.substring(0, MAX_ENDPOINT_LENGTH);
		}
		return new CallContext(clientId, endpoint, isNoCache(request.getHeader("Cache-Control")));
	}

	private static boolean isNoCache(String cacheControl) {
		if (cacheControl == null) {
			return false;
		}
		String value = cacheControl.toLowerCase(Locale.ROOT);
		return value.contains("no-cache") || value.contains("no-store");
	}

}

package br.com.doistecht.iaservice.gateway;

import br.com.doistecht.iaservice.client.AuthenticatedClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Dados da requisição que originou a chamada ao modelo.
 * <p>
 * Precisa ser capturado na thread da requisição: no streaming, o fim da resposta
 * acontece em outra thread, onde a requisição já não está disponível. Trabalhos em
 * segundo plano informam o cliente com {@link UsageAttribution}.
 *
 * @param clientId    cliente autenticado, ou {@code null} fora de uma requisição de cliente
 * @param clientName  nome do cliente, usado nas métricas
 * @param clientDefaultProvider provedor padrão do cliente, ou {@code null}
 * @param endpoint    rota chamada
 * @param bypassCache {@code true} quando o cliente enviou {@code Cache-Control: no-cache}
 */
record CallContext(Long clientId, String clientName, String clientDefaultProvider, String endpoint,
		boolean bypassCache) {

	private static final int MAX_ENDPOINT_LENGTH = 200;

	static final CallContext NONE = new CallContext(null, null, null, "internal", false);

	static CallContext current() {
		CallContext attributed = UsageAttribution.current();
		if (attributed != null) {
			return attributed;
		}
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return NONE;
		}
		HttpServletRequest request = attributes.getRequest();
		AuthenticatedClient client = request.getAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE) instanceof AuthenticatedClient c
				? c
				: null;
		String endpoint = request.getRequestURI();
		if (endpoint.length() > MAX_ENDPOINT_LENGTH) {
			endpoint = endpoint.substring(0, MAX_ENDPOINT_LENGTH);
		}
		if (client == null) {
			return new CallContext(null, null, null, endpoint, isNoCache(request.getHeader("Cache-Control")));
		}
		return new CallContext(client.id(), client.name(), client.defaultProvider(), endpoint,
				isNoCache(request.getHeader("Cache-Control")));
	}

	private static boolean isNoCache(String cacheControl) {
		if (cacheControl == null) {
			return false;
		}
		String value = cacheControl.toLowerCase(Locale.ROOT);
		return value.contains("no-cache") || value.contains("no-store");
	}

}

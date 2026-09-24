package br.com.doistecht.iaservice.security;

import br.com.doistecht.iaservice.client.AuthenticatedClient;
import br.com.doistecht.iaservice.client.ClientAuthenticator;
import br.com.doistecht.iaservice.config.IaServiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Exige o header {@code X-API-Key} em todas as rotas da API.
 * <ul>
 * <li>{@code /v1/admin/**}: chave de administrador ({@code ia-service.admin-key});</li>
 * <li>demais rotas {@code /v1/**}: chave de um cliente cadastrado e ativo.</li>
 * </ul>
 * O cliente autenticado fica no atributo {@link AuthenticatedClient#REQUEST_ATTRIBUTE}.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-API-Key";

	private static final String ADMIN_PATH = "/v1/admin/";

	private final byte[] adminKey;

	private final ClientAuthenticator authenticator;

	private final ObjectMapper objectMapper;

	public ApiKeyFilter(IaServiceProperties properties, ClientAuthenticator authenticator,
			ObjectMapper objectMapper) {
		this.adminKey = properties.adminKey().getBytes(StandardCharsets.UTF_8);
		this.authenticator = authenticator;
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !path(request).startsWith("/v1/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String providedKey = request.getHeader(HEADER);

		if (path(request).startsWith(ADMIN_PATH)) {
			if (!isAdminKey(providedKey)) {
				writeUnauthorized(request, response, "Chave de administrador ausente ou inválida.");
				return;
			}
			chain.doFilter(request, response);
			return;
		}

		Optional<AuthenticatedClient> client = authenticator.authenticate(providedKey);
		if (client.isEmpty()) {
			writeUnauthorized(request, response, "API key ausente, inválida ou desativada.");
			return;
		}
		request.setAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE, client.get());
		chain.doFilter(request, response);
	}

	// Comparação em tempo constante para não vazar informação por tempo de resposta
	private boolean isAdminKey(String providedKey) {
		return providedKey != null && MessageDigest.isEqual(adminKey, providedKey.getBytes(StandardCharsets.UTF_8));
	}

	private static String path(HttpServletRequest request) {
		return request.getRequestURI().substring(request.getContextPath().length());
	}

	private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String detail)
			throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				detail + " Envie o header " + HEADER + ".");
		problem.setInstance(URI.create(request.getRequestURI()));

		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), problem);
	}

}

package br.com.doistecht.iaservice.security;

import br.com.doistecht.iaservice.client.AuthenticatedClient;
import br.com.doistecht.iaservice.client.ClientAuthenticator;
import br.com.doistecht.iaservice.config.IaServiceProperties;
import br.com.doistecht.iaservice.security.admin.AdminAccessService;
import br.com.doistecht.iaservice.security.admin.AdminPrincipal;
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
import org.springframework.http.HttpHeaders;
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

	private static final String ADMIN_AUTH_PATH = "/v1/admin/auth/";

	private static final String ADMIN_LOGIN_PATH = "/v1/admin/auth/login";

	private final byte[] adminKey;

	private final ClientAuthenticator authenticator;

	private final AdminAccessService adminAccessService;

	private final ObjectMapper objectMapper;

	public ApiKeyFilter(IaServiceProperties properties, ClientAuthenticator authenticator,
			AdminAccessService adminAccessService, ObjectMapper objectMapper) {
		this.adminKey = properties.adminKey().getBytes(StandardCharsets.UTF_8);
		this.authenticator = authenticator;
		this.adminAccessService = adminAccessService;
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

		String path = path(request);
		if (path.startsWith(ADMIN_PATH)) {
			handleAdmin(request, response, chain, path, providedKey);
			return;
		}

		Optional<AuthenticatedClient> client = authenticator.authenticate(providedKey);
		if (client.isEmpty()) {
			writeProblem(request, response, HttpStatus.UNAUTHORIZED,
					"API key ausente, inválida ou desativada. Envie o header " + HEADER + ".");
			return;
		}
		request.setAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE, client.get());
		chain.doFilter(request, response);
	}

	private void handleAdmin(HttpServletRequest request, HttpServletResponse response, FilterChain chain, String path,
			String providedKey) throws IOException, ServletException {
		if (ADMIN_LOGIN_PATH.equals(path) && "POST".equals(request.getMethod())) {
			chain.doFilter(request, response);
			return;
		}

		var principal = adminAccessService.authenticate(bearerToken(request));
		if (path.startsWith(ADMIN_AUTH_PATH)) {
			if (principal.isEmpty()) {
				writeProblem(request, response, HttpStatus.UNAUTHORIZED, "Sessão administrativa ausente ou expirada.");
				return;
			}
			request.setAttribute(AdminPrincipal.REQUEST_ATTRIBUTE, principal.get());
			chain.doFilter(request, response);
			return;
		}

		// Mantém a chave administrativa legada para automações e recuperação de emergência.
		if (isAdminKey(providedKey)) {
			chain.doFilter(request, response);
			return;
		}
		if (principal.isEmpty()) {
			writeProblem(request, response, HttpStatus.UNAUTHORIZED, "Sessão administrativa ausente ou expirada.");
			return;
		}
		if (principal.get().mustChangePassword()) {
			writeProblem(request, response, HttpStatus.FORBIDDEN,
					"A troca da senha inicial é obrigatória antes de acessar o portal.");
			return;
		}
		request.setAttribute(AdminPrincipal.REQUEST_ATTRIBUTE, principal.get());
		chain.doFilter(request, response);
	}

	private static String bearerToken(HttpServletRequest request) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		return authorization != null && authorization.startsWith("Bearer ")
				? authorization.substring("Bearer ".length()).strip()
				: null;
	}

	// Comparação em tempo constante para não vazar informação por tempo de resposta
	private boolean isAdminKey(String providedKey) {
		return providedKey != null && MessageDigest.isEqual(adminKey, providedKey.getBytes(StandardCharsets.UTF_8));
	}

	private static String path(HttpServletRequest request) {
		return request.getRequestURI().substring(request.getContextPath().length());
	}

	private void writeProblem(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
			throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setInstance(URI.create(request.getRequestURI()));

		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), problem);
	}

}

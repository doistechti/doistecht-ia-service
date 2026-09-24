package br.com.doistecht.iaservice.security;

import br.com.doistecht.iaservice.config.IaServiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Exige o header {@code X-API-Key} em todas as rotas da API.
 * <p>
 * Na fase 1 existe uma única chave, configurada por variável de ambiente.
 * Na fase 3 esta validação passa a consultar os clientes cadastrados no banco.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-API-Key";

	private final byte[] expectedKey;

	private final ObjectMapper objectMapper;

	public ApiKeyFilter(IaServiceProperties properties, ObjectMapper objectMapper) {
		this.expectedKey = properties.apiKey().getBytes(StandardCharsets.UTF_8);
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return !path.startsWith("/v1/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String providedKey = request.getHeader(HEADER);
		if (providedKey == null || !isValid(providedKey)) {
			writeUnauthorized(request, response);
			return;
		}
		chain.doFilter(request, response);
	}

	// Comparação em tempo constante para não vazar informação por tempo de resposta
	private boolean isValid(String providedKey) {
		return MessageDigest.isEqual(expectedKey, providedKey.getBytes(StandardCharsets.UTF_8));
	}

	private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"API key ausente ou inválida. Envie o header " + HEADER + ".");
		problem.setInstance(URI.create(request.getRequestURI()));

		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), problem);
	}

}

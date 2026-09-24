package br.com.doistecht.iaservice.exception;

import br.com.doistecht.iaservice.provider.AiProviderException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte exceções em respostas no formato {@link ProblemDetail} (RFC 9457).
 * <p>
 * Erros do Spring MVC (validação, JSON malformado, etc.) já são tratados pela
 * classe base {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(AiProviderException.class)
	ProblemDetail handleAiProvider(AiProviderException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
				"Não foi possível obter resposta do provedor de IA. Tente novamente em instantes.");
		problem.setProperty("provider", ex.getProvider());
		return problem;
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		ex.getBody().setDetail("Um ou mais campos são inválidos.");
		ex.getBody().setProperty("errors", errors);
		return super.handleMethodArgumentNotValid(ex, headers, status, request);
	}

}

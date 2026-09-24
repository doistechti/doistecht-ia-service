package br.com.doistecht.iaservice.exception;

import br.com.doistecht.iaservice.client.ClientAlreadyExistsException;
import br.com.doistecht.iaservice.client.ClientNotFoundException;
import br.com.doistecht.iaservice.document.DocumentNotFoundException;
import br.com.doistecht.iaservice.document.InvalidDocumentException;
import br.com.doistecht.iaservice.provider.AiProviderException;
import br.com.doistecht.iaservice.ratelimit.RateLimitExceededException;
import br.com.doistecht.iaservice.structured.InvalidSchemaException;
import br.com.doistecht.iaservice.structured.InvalidStructuredOutputException;
import br.com.doistecht.iaservice.template.MissingVariablesException;
import br.com.doistecht.iaservice.template.TemplateNotFoundException;
import br.com.doistecht.iaservice.usage.InvalidPeriodException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
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

	/**
	 * {@code 503} quando o provedor está fora do ar (vale tentar mais tarde);
	 * {@code 502} quando o provedor recusou a chamada.
	 */
	@ExceptionHandler(AiProviderException.class)
	ResponseEntity<ProblemDetail> handleAiProvider(AiProviderException ex) {
		boolean unavailable = ex.getReason() == AiProviderException.Reason.UNAVAILABLE;
		HttpStatus status = unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, unavailable
				? "O provedor de IA está indisponível no momento. Tente novamente em instantes."
				: "Não foi possível obter resposta do provedor de IA.");
		problem.setProperty("provider", ex.getProvider());

		var response = ResponseEntity.status(status);
		if (ex.getRetryAfterSeconds() != null) {
			problem.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
			response.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
		}
		return response.body(problem);
	}

	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
		problem.setProperty("limit", ex.getLimit());
		problem.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
				.body(problem);
	}

	@ExceptionHandler({ TemplateNotFoundException.class, ClientNotFoundException.class,
			DocumentNotFoundException.class })
	ProblemDetail handleNotFound(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(ClientAlreadyExistsException.class)
	ProblemDetail handleClientAlreadyExists(ClientAlreadyExistsException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(MissingVariablesException.class)
	ProblemDetail handleMissingVariables(MissingVariablesException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Variáveis obrigatórias do template não foram informadas.");
		problem.setProperty("missingVariables", ex.getMissing());
		return problem;
	}

	@ExceptionHandler({ InvalidSchemaException.class, InvalidPeriodException.class, InvalidDocumentException.class })
	ProblemDetail handleBadRequest(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(InvalidStructuredOutputException.class)
	ProblemDetail handleInvalidStructuredOutput(InvalidStructuredOutputException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
				"O modelo não gerou uma resposta válida para o schema informado.");
		problem.setProperty("errors", ex.getErrors());
		return problem;
	}

	// Ex.: dois pedidos simultâneos tentando criar a mesma versão de template
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Conflito ao gravar os dados. Tente novamente.");
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

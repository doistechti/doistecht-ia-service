package br.com.doistecht.iaservice.config;

import java.nio.charset.StandardCharsets;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Declara {@code charset=UTF-8} nas respostas JSON ({@code application/json} e
 * {@code application/problem+json}).
 * <p>
 * O JSON é UTF-8 por definição e o Spring não envia o charset, mas clientes como o
 * PowerShell 5.1 ({@code Invoke-RestMethod}) assumem Latin-1 quando ele não vem no header
 * e mostram "Ã©" no lugar de "é". Declarar o charset não afeta os demais clientes.
 */
@RestControllerAdvice
class Utf8JsonResponseAdvice implements ResponseBodyAdvice<Object> {

	@Override
	public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
		return true;
	}

	@Override
	public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
			Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
			ServerHttpResponse response) {
		if (selectedContentType.getCharset() == null && isJson(selectedContentType)) {
			response.getHeaders().setContentType(new MediaType(selectedContentType, StandardCharsets.UTF_8));
		}
		return body;
	}

	private static boolean isJson(MediaType mediaType) {
		return MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
				|| MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(mediaType);
	}

}

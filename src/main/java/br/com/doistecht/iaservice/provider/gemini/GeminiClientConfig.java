package br.com.doistecht.iaservice.provider.gemini;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Cliente HTTP do Gemini, substituindo o criado pelo Spring AI.
 * <p>
 * Por padrão, o SDK do Google não tem timeout e repete sozinho até 5 vezes as chamadas
 * que falham com 408/429/5xx. Aqui o timeout é definido e o retry do SDK é desligado:
 * novas tentativas ficam só com o Resilience4j, em um único lugar configurável.
 */
@Configuration(proxyBeanMethods = false)
class GeminiClientConfig {

	@Bean
	Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey, GeminiProperties properties) {
		HttpOptions.Builder httpOptions = HttpOptions.builder()
				.timeout((int) properties.timeout().toMillis())
				.retryOptions(HttpRetryOptions.builder().attempts(1).build());
		if (StringUtils.hasText(properties.baseUrl())) {
			httpOptions.baseUrl(properties.baseUrl());
		}
		return Client.builder()
				.apiKey(apiKey)
				.vertexAI(false)
				.httpOptions(httpOptions.build())
				.build();
	}

}

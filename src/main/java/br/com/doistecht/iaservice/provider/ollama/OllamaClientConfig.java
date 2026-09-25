package br.com.doistecht.iaservice.provider.ollama;

import java.net.http.HttpClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Cliente HTTP do Ollama, substituindo o criado pelo Spring AI, que não tem timeout.
 * <p>
 * Conexão com timeout curto (o Ollama costuma rodar na mesma rede; se não responder,
 * está desligado) e leitura com timeout longo (modelos locais em CPU demoram).
 */
@Configuration(proxyBeanMethods = false)
class OllamaClientConfig {

	@Bean
	OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl, OllamaProperties properties) {
		// O Ollama fala HTTP/1.1; sem fixar a versão, o cliente do JDK tenta um upgrade para HTTP/2
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(properties.connectTimeout())
				.build();

		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		JdkClientHttpConnector connector = new JdkClientHttpConnector(httpClient);
		connector.setReadTimeout(properties.readTimeout());

		return OllamaApi.builder()
				.baseUrl(baseUrl)
				.restClientBuilder(RestClient.builder().requestFactory(requestFactory))
				.webClientBuilder(WebClient.builder().clientConnector(connector))
				.build();
	}

}

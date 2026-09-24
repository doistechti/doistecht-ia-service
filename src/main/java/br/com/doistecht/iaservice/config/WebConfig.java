package br.com.doistecht.iaservice.config;

import br.com.doistecht.iaservice.ratelimit.RateLimitInterceptor;
import br.com.doistecht.iaservice.ratelimit.RateLimitService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final RateLimitService rateLimitService;

	public WebConfig(RateLimitService rateLimitService) {
		this.rateLimitService = rateLimitService;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// Apenas as rotas que chamam o modelo consomem limite e cota
		registry.addInterceptor(new RateLimitInterceptor(rateLimitService))
				.addPathPatterns("/v1/chat/**", "/v1/structured/**", "/v1/tasks/**", "/v1/embeddings/**",
						"/v1/documents/**", "/v1/rag/**");
	}

}

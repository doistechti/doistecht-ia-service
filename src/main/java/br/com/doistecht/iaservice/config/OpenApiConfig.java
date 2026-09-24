package br.com.doistecht.iaservice.config;

import br.com.doistecht.iaservice.security.ApiKeyFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String API_KEY_SCHEME = "apiKey";

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("doistecht-ia-service")
						.description("""
								AI Gateway que disponibiliza modelos de IA para diversos projetos.

								Autenticação pelo header `X-API-Key`:
								- rotas `/v1/admin/**`: chave de administrador (`IA_SERVICE_ADMIN_KEY`);
								- demais rotas: API key do cliente, gerada em `POST /v1/admin/clients`.""")
						.version("v1"))
				.components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
						.type(SecurityScheme.Type.APIKEY)
						.in(SecurityScheme.In.HEADER)
						.name(ApiKeyFilter.HEADER)))
				.addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
	}

}

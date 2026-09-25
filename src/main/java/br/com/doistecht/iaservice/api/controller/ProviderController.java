package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.ProviderResponse;
import br.com.doistecht.iaservice.gateway.ProviderRouter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Provedores", description = "Provedores de IA disponíveis")
@RestController
@RequestMapping("/v1/providers")
public class ProviderController {

	private final ProviderRouter router;

	public ProviderController(ProviderRouter router) {
		this.router = router;
	}

	@Operation(summary = "Lista os provedores habilitados, seus modelos e a situação de cada um",
			description = "Use o nome do provedor no campo \"provider\" das requisições para escolher onde executar.")
	@GetMapping
	public List<ProviderResponse> list() {
		return router.all().stream()
				.sorted(Comparator.comparing(provider -> provider.name()))
				.map(provider -> ProviderResponse.from(provider, provider.name().equals(router.defaultProvider()),
						provider.name().equals(router.fallbackProvider())))
				.toList();
	}

}

package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.UsageResponse;
import br.com.doistecht.iaservice.client.AuthenticatedClient;
import br.com.doistecht.iaservice.usage.UsageService;
import br.com.doistecht.iaservice.usage.UsageService.Period;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Uso", description = "Consumo de requisições e tokens")
@RestController
public class UsageController {

	private final UsageService usageService;

	public UsageController(UsageService usageService) {
		this.usageService = usageService;
	}

	@Operation(summary = "Consumo do cliente autenticado no período (padrão: últimos 30 dias)")
	@GetMapping("/v1/usage")
	public UsageResponse usage(
			@Parameter(description = "Data inicial (UTC, inclusiva)", example = "2026-09-01")
			@RequestParam(required = false) LocalDate from,
			@Parameter(description = "Data final (UTC, inclusiva)", example = "2026-09-30")
			@RequestParam(required = false) LocalDate to,
			@Parameter(hidden = true) @RequestAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE) AuthenticatedClient client) {
		Period period = Period.of(from, to);
		return new UsageResponse(period.from(), period.to(), usageService.totalsForClient(client.id(), period));
	}

}

package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.AdminUsageResponse;
import br.com.doistecht.iaservice.usage.UsageService;
import br.com.doistecht.iaservice.usage.UsageService.Period;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Uso", description = "Consumo agregado de todos os clientes")
@RestController
public class UsageAdminController {

	private final UsageService usageService;

	public UsageAdminController(UsageService usageService) {
		this.usageService = usageService;
	}

	@Operation(summary = "Consumo agregado por cliente no período (padrão: últimos 30 dias)")
	@GetMapping("/v1/admin/usage")
	public AdminUsageResponse adminUsage(
			@Parameter(description = "Data inicial (UTC, inclusiva)", example = "2026-09-01")
			@RequestParam(required = false) LocalDate from,
			@Parameter(description = "Data final (UTC, inclusiva)", example = "2026-09-30")
			@RequestParam(required = false) LocalDate to) {
		Period period = Period.of(from, to);
		return new AdminUsageResponse(period.from(), period.to(), usageService.totalsByClient(period));
	}

}

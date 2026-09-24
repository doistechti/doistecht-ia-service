package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.usage.UsageTotals;
import java.time.LocalDate;

/** Uso do cliente autenticado no período (datas UTC, inclusivas). */
public record UsageResponse(LocalDate from, LocalDate to, UsageTotals totals) {
}

package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.usage.ClientUsage;
import java.time.LocalDate;
import java.util.List;

/** Uso agregado por cliente no período (datas UTC, inclusivas), do maior para o menor. */
public record AdminUsageResponse(LocalDate from, LocalDate to, List<ClientUsage> clients) {
}

package br.com.doistecht.iaservice.usage;

public record ClientUsage(Long clientId, String clientName, UsageTotals totals) {
}

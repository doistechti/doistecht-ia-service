package br.com.doistecht.iaservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ClientCreateRequest(

		@Schema(description = "Nome do projeto cliente", example = "portal-atendimento")
		@NotBlank
		@Size(max = 100)
		String name,

		@Schema(description = "Requisições por minuto (padrão 10)", example = "10")
		@Positive
		@Max(10_000)
		Integer rateLimitPerMinute,

		@Schema(description = "Requisições por dia (padrão 200)", example = "200")
		@Positive
		@Max(1_000_000)
		Integer dailyQuota) {

	// Padrões próximos aos limites do plano gratuito do Gemini
	public static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 10;

	public static final int DEFAULT_DAILY_QUOTA = 200;

	public int rateLimitPerMinuteOrDefault() {
		return rateLimitPerMinute == null ? DEFAULT_RATE_LIMIT_PER_MINUTE : rateLimitPerMinute;
	}

	public int dailyQuotaOrDefault() {
		return dailyQuota == null ? DEFAULT_DAILY_QUOTA : dailyQuota;
	}

}

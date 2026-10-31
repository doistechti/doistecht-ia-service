package br.com.doistecht.iaservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ClientApiKeyResponse(@Schema(description = "API key completa do cliente") String apiKey) {
}

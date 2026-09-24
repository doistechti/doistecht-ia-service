package br.com.doistecht.iaservice.provider;

/**
 * Tokens consumidos em uma chamada ao modelo. Campos nulos indicam que o
 * provedor não informou o valor.
 */
public record TokenUsage(Integer promptTokens, Integer outputTokens) {
}

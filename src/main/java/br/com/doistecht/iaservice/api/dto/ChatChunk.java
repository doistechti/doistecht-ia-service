package br.com.doistecht.iaservice.api.dto;

/**
 * Parte de uma resposta em streaming. Enviada como JSON para que quebras de
 * linha no texto não interfiram no formato do Server-Sent Events.
 */
public record ChatChunk(String content) {
}

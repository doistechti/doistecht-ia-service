package br.com.doistecht.iaservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminChangePasswordRequest(
		@NotBlank @Size(max = 128) String currentPassword,
		@NotBlank @Size(min = 12, max = 128) String newPassword) {
}

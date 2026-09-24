package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.provider.ChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record ChatMessageDto(

		@Schema(description = "Autor da mensagem", allowableValues = { "user", "assistant" }, example = "user")
		@NotBlank
		@Pattern(regexp = "user|assistant", message = "deve ser 'user' ou 'assistant'")
		String role,

		@Schema(description = "Texto da mensagem", example = "Qual a capital do Brasil?")
		@NotBlank
		@Size(max = 10_000)
		String content) {

	public ChatMessage toMessage() {
		return new ChatMessage(ChatMessage.Role.valueOf(role.toUpperCase(Locale.ROOT)), content);
	}

}

package br.com.doistecht.iaservice.api.dto;

import br.com.doistecht.iaservice.provider.ChatCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatRequest(

		@Schema(description = "Mensagem do usuário", example = "Explique o que é um AI Gateway em uma frase.")
		@NotBlank
		@Size(max = 10_000)
		String message,

		@Schema(description = "Instrução de comportamento do modelo (opcional)", example = "Responda de forma objetiva.")
		@Size(max = 4_000)
		String systemPrompt,

		@Schema(description = "Mensagens anteriores da conversa, da mais antiga para a mais recente (opcional). "
				+ "O serviço não guarda histórico: o cliente envia a conversa a cada chamada.")
		@Size(max = 50)
		List<@Valid ChatMessageDto> history,

		@Schema(description = "Provedor de IA (opcional): gemini ou ollama. Sem ele, usa o padrão do cliente ou o global, "
				+ "com troca automática para o provedor reserva se o escolhido estiver fora do ar.", example = "gemini")
		@Pattern(regexp = "[a-z0-9-]{1,30}", message = "nome de provedor inválido")
		String provider) {

	public ChatCommand toCommand() {
		var messages = history == null ? null : history.stream().map(ChatMessageDto::toMessage).toList();
		return new ChatCommand(systemPrompt, messages, message, provider);
	}

}

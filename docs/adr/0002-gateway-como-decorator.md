# ADR 0002 — Cache, uso e métricas em um decorator (gateway)

**Status:** aceita (fase 3, ampliada nas fases 5 e 6)

## Contexto

Toda chamada a um modelo precisa de cache, registro de uso (tokens, latência, custo), métricas e, a partir da fase 6, escolha de provedor e troca para o reserva. Espalhar isso por controllers e serviços duplicaria lógica e seria fácil de esquecer.

## Decisão

Concentrar essas responsabilidades no `GatewayAiProvider`, que implementa `AiProvider` e é o único bean dessa interface. Controllers e serviços continuam chamando `aiProvider.chat(...)` sem saber de cache, uso ou provedores.

## Alternativas consideradas

- **Advisors do Spring AI:** o mecanismo "oficial" para interceptar chamadas, mas ligado ao Spring AI (contraria o [ADR 0001](0001-spring-ai-isolado.md)) e sem visão de vários provedores.
- **AOP (aspectos):** menos explícito e mais difícil de testar.

## Consequências

- Os comportamentos transversais ficam em um lugar só e são testados sem subir o Spring (`GatewayAiProviderTest`, `GatewayProviderFallbackTest`).
- Algumas regras ficaram explícitas no decorator: respostas estruturadas só entram no cache se seguirem o schema, e respostas de reserva nunca entram no cache.
- Chamadas feitas fora de uma requisição HTTP (processamento de documentos) informam o cliente com `UsageAttribution`, para o uso não se perder.

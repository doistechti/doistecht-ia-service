# ADR 0001 — Spring AI isolado atrás de interfaces próprias

**Status:** aceita (fase 1, ampliada na fase 6)

## Contexto

O serviço precisa integrar modelos de IA de mais de um provedor. O Spring AI oferece a integração com o Spring Boot (starters, configuração, métricas), mas é uma biblioteca nova, com mudanças frequentes de API entre versões, e nem sempre expõe tudo o que o provedor oferece.

## Decisão

Usar o Spring AI, mas só dentro do pacote `provider`. O restante da aplicação conhece duas interfaces do projeto:

- `AiProvider`: a porta de entrada usada por controllers e serviços, implementada pelo gateway;
- `ModelProvider`: o contrato de cada provedor concreto (Gemini, Ollama).

RAG, templates de prompt, cache, rate limit e registro de uso são código do projeto, sem dependência do Spring AI.

## Alternativas consideradas

- **LangChain4j:** mais flexível para agentes e RAG avançado, mas menos integrado ao Spring Boot.
- **Usar o Spring AI em toda a aplicação** (ChatClient, Advisors, VectorStore): menos código, mas acoplaria a aplicação inteira a uma API ainda instável.

## Consequências

- Trocar ou atualizar a biblioteca afeta só o pacote `provider`.
- Adicionar o Ollama na fase 6 não exigiu mudança em controllers nem serviços.
- Quando o Spring AI ficou devendo (o `task-type` dos embeddings, ver [ADR 0006](0006-embeddings-pelo-sdk-do-google.md)), foi possível usar o SDK do provedor direto, sem afetar o resto.
- Custo: mais código próprio (renderizador de templates, chunking, busca vetorial).

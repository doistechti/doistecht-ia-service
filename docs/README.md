# Documentação — doistecht-ia-service

| # | Documento | Descrição |
|---|---|---|
| 01 | [Escopo do Projeto](01-escopo-do-projeto.md) | Visão geral, funcionalidades, stack e roadmap |
| 02 | [Fase 1 — MVP](02-fase-1-mvp.md) | Spring Boot + `/v1/chat` com Gemini, API key fixa, Swagger, Docker |
| 03 | [Fase 2 — Produto](03-fase-2-produto.md) | Streaming, output estruturado, templates de prompt versionados |
| 04 | [Fase 3 — Governança](04-fase-3-governanca.md) | Multi-tenant, rate limit, cotas, registro de uso, cache |
| 05 | [Fase 4 — Robustez](05-fase-4-robustez.md) | Resilience4j, testes com WireMock e Testcontainers, CI |
| 06 | [Fase 5 — Diferencial](06-fase-5-diferencial.md) | Embeddings, RAG com pgvector, Prometheus e Grafana |
| 07 | [Fase 6 — Extra](07-fase-6-extra.md) | Provedor Ollama, roteamento, demo, validação com o Gemini real |
| 08 | [Guia da API](08-guia-da-api.md) | Exemplos de uso de todas as rotas |
| — | [ADRs](adr) | Decisões técnicas, com contexto e alternativas |

As fases foram escritas como registro do desenvolvimento: cada uma mostra o estado do projeto quando foi concluída. Os modelos padrão do Gemini, por exemplo, mudaram na fase 6 (de `gemini-2.5-flash` para `gemini-3.8-flash`).

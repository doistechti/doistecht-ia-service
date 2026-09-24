# Escopo do Projeto — doistecht-ia-service

## 1. Visão geral

O **doistecht-ia-service** é um microsserviço que funciona como um **AI Gateway**: uma camada única entre os projetos clientes e os provedores de modelos de IA.

Os projetos não chamam o provedor de IA (Gemini) diretamente. Eles consomem a API deste serviço, que é responsável por:

- autenticação dos clientes;
- controle de limites e cotas de uso;
- cache de respostas;
- gerenciamento de prompts;
- observabilidade e métricas;
- abstração e troca de provedor de IA.

### Objetivos

- Disponibilizar modelos de IA de forma padronizada para diversos projetos.
- Compor portfólio demonstrando boas práticas de backend, arquitetura e engenharia de software.
- Servir como base de estudo e fixação dos conceitos de integração com LLMs.

## 2. Arquitetura

```
[Projeto A] ─┐
[Projeto B] ─┼──> doistecht-ia-service ──> Gemini (free)
[Projeto C] ─┘         │                 └─> (futuro: Ollama, OpenAI…)
                       ├─ PostgreSQL (clientes, prompts, uso)
                       └─ Redis (cache, rate limit)
```

## 3. Funcionalidades

### 3.1 Núcleo de IA

| Endpoint | Descrição |
|---|---|
| `POST /v1/chat` | Conversa simples ou com histórico de mensagens |
| `POST /v1/chat/stream` | Resposta em streaming via SSE (Server-Sent Events) |
| `POST /v1/structured` | Resposta em JSON validado contra um schema (ex.: extração de dados de texto) |
| `POST /v1/embeddings` | Geração de vetores para busca semântica |

**Abstração de provedor:** interface `AiProvider` com implementações por provedor (Gemini inicialmente; Ollama e outros no futuro), aplicando os padrões Strategy/Adapter.

### 3.2 Casos de uso prontos (templates de prompt)

- Registro de prompts **versionados** no banco de dados. Exemplos:
  - `resumir-texto@v2`
  - `classificar-ticket@v1`
  - `gerar-descricao-produto@v1`
- Endpoint `POST /v1/tasks/{nome-do-template}` recebendo apenas as variáveis do template.
- O cliente não precisa conhecer engenharia de prompt: apenas consome a tarefa.

### 3.3 Multi-tenant e governança

- **API Key por projeto cliente**, armazenada com hash.
- **Rate limit e cota diária por cliente** (Bucket4j + Redis). Essencial por conta dos limites de requisições por minuto e por dia do plano gratuito do Gemini.
- **Registro de uso** por requisição: tokens de entrada/saída, latência, modelo utilizado e custo estimado por cliente.

### 3.4 Resiliência

- Retry com backoff exponencial e circuit breaker (Resilience4j) para erros 429/503 do provedor.
- Cache de respostas idênticas (Redis), economizando a cota gratuita.
- Fallback para modelo mais leve quando o modelo principal estiver indisponível.

### 3.5 RAG (Retrieval-Augmented Generation)

- Upload de documentos → divisão em trechos (chunking) → geração de embeddings → armazenamento no pgvector.
- Endpoint `POST /v1/rag/ask`: responde perguntas com base nos documentos do próprio cliente.

### 3.6 Engenharia e qualidade

- Documentação da API com OpenAPI/Swagger.
- Testes de integração com **WireMock** (simulando o Gemini) e **Testcontainers** (PostgreSQL/Redis).
- Observabilidade com Micrometer + Prometheus + Grafana (dashboard de tokens e latência por cliente).
- Ambiente completo via Docker Compose.
- Pipeline de CI com GitHub Actions.

## 4. Stack tecnológica

| Item | Escolha |
|---|---|
| Linguagem | Java 21 (virtual threads) |
| Framework | Spring Boot 4 |
| Integração com IA | Spring AI 2.0 |
| Build | Maven |
| Banco de dados | PostgreSQL + pgvector, migrações com Flyway |
| Cache / rate limit | Redis + Bucket4j |
| Resiliência | Resilience4j |
| Observabilidade | Spring Boot Actuator, Micrometer, Prometheus, Grafana |
| Documentação da API | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5, WireMock, Testcontainers |
| Infraestrutura | Docker, Docker Compose, GitHub Actions |

### Provedor de IA

- **Gemini** via chave gratuita do **Google AI Studio**.
- **Atenção:** no plano gratuito, os dados enviados podem ser utilizados pelo Google para melhoria dos modelos. **Não enviar dados sensíveis.**

## 5. Roadmap

| Fase | Nome | Entregas | Detalhamento |
|---|---|---|---|
| 1 | MVP | Projeto Spring Boot, `/v1/chat` integrado ao Gemini, API key fixa, Swagger, Docker | [02-fase-1-mvp.md](02-fase-1-mvp.md) |
| 2 | Produto | Streaming (SSE), templates de prompt no banco, output estruturado | [03-fase-2-produto.md](03-fase-2-produto.md) |
| 3 | Governança | Multi-tenant, rate limit, cotas, registro de uso, cache | [04-fase-3-governanca.md](04-fase-3-governanca.md) |
| 4 | Robustez | Resilience4j, testes com WireMock e Testcontainers, CI | [05-fase-4-robustez.md](05-fase-4-robustez.md) |
| 5 | Diferencial | Embeddings + RAG com pgvector, dashboard no Grafana | [06-fase-5-diferencial.md](06-fase-5-diferencial.md) |
| 6 | Extra | Segundo provedor (Ollama) comprovando a abstração | [07-fase-6-extra.md](07-fase-6-extra.md) |

## 6. Projeto cliente de demonstração

Para evidenciar o uso por "diversos projetos", será criado um projeto cliente simples (front-end ou bot) que consome o serviço, demonstrando os endpoints na prática.

## 7. Fora do escopo (neste momento)

- Treinamento ou fine-tuning de modelos.
- Interface administrativa completa (gestão será via API/banco inicialmente).
- Cobrança real de clientes (o custo é apenas estimado para métricas).

## 8. Decisões

- [x] Ferramenta de build: **Maven**.
- [x] Biblioteca de integração com IA: **Spring AI** (integração nativa com Spring Boot, observabilidade via Micrometer e Advisors para cache/uso/RAG). A biblioteca fica isolada atrás da interface `AiProvider`.
- [ ] Modelo Gemini padrão e modelo de fallback.

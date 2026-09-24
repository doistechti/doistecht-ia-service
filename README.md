# doistecht-ia-service

[![CI](https://github.com/doistechti/doistecht-ia-service/actions/workflows/ci.yml/badge.svg)](https://github.com/doistechti/doistecht-ia-service/actions/workflows/ci.yml)

AI Gateway em Java que disponibiliza modelos de IA para diversos projetos por meio de uma API única.

Os projetos clientes não chamam o provedor de IA diretamente: eles consomem este serviço, que centraliza autenticação, controle de uso, prompts e observabilidade.

> **Status:** Fase 4 (Robustez) concluída — veja o [roadmap](docs/01-escopo-do-projeto.md#5-roadmap).

## Funcionalidades

- **Chat** com histórico de conversa: `POST /v1/chat`
- **Streaming** da resposta via Server-Sent Events: `POST /v1/chat/stream`
- **Output estruturado**: JSON validado contra um JSON Schema: `POST /v1/structured`
- **Tarefas prontas** a partir de templates de prompt versionados: `POST /v1/tasks/{template}`
- **Gestão de templates**, globais ou por cliente: `/v1/admin/templates`
- **Multi-tenant**: cada projeto cliente tem sua própria API key: `/v1/admin/clients`
- **Rate limit por minuto e cota diária** por cliente (Bucket4j + Redis)
- **Cache de respostas** por cliente no Redis
- **Registro de uso**: tokens, latência e custo estimado por chamada: `/v1/usage` e `/v1/admin/usage`
- **Resiliência**: retry, circuit breaker e modelo reserva quando o Gemini falha

## Stack

- Java 21 (virtual threads)
- Spring Boot 4.1 + Spring AI 2.0
- Gemini (Google AI Studio)
- PostgreSQL 17 + Flyway + Spring Data JPA
- Redis 8 + Bucket4j + Caffeine
- Resilience4j
- Maven
- springdoc-openapi (Swagger UI)
- JUnit 5, Mockito, Testcontainers, WireMock, JaCoCo
- GitHub Actions
- Docker / Docker Compose

## Como executar

### Pré-requisitos

- Chave gratuita do Gemini: [Google AI Studio](https://aistudio.google.com/apikey)
- Docker **ou** Java 21 + Maven (neste caso, Docker ainda é usado para o PostgreSQL e o Redis)

### Configuração

```bash
cp .env.example .env
# edite o .env e preencha GEMINI_API_KEY e IA_SERVICE_ADMIN_KEY
```

| Variável | Obrigatória | Descrição |
|---|---|---|
| `GEMINI_API_KEY` | Sim | Chave do Google AI Studio |
| `GEMINI_MODEL` | Não | Modelo Gemini (padrão `gemini-2.5-flash`) |
| `GEMINI_FALLBACK_MODEL` | Não | Modelo reserva (padrão `gemini-2.5-flash-lite`; vazio desativa) |
| `GEMINI_TIMEOUT` | Não | Tempo máximo de cada chamada ao Gemini (padrão `30s`) |
| `IA_SERVICE_ADMIN_KEY` | Sim | Chave de administrador, exigida nas rotas `/v1/admin/**` |
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | Não | Credenciais do PostgreSQL (padrão `ia_service`) |
| `DB_URL` | Não | URL JDBC (padrão `jdbc:postgresql://localhost:5433/ia_service`) |
| `REDIS_HOST` / `REDIS_PORT` | Não | Redis (padrão `localhost:6379`) |
| `RESPONSE_CACHE_ENABLED` / `RESPONSE_CACHE_TTL` | Não | Liga o cache de respostas e define a validade (padrão `true` / `1h`) |

> A partir da fase 3, `IA_SERVICE_API_KEY` foi substituída por `IA_SERVICE_ADMIN_KEY`: os clientes passaram a ter chaves próprias.

### Com Docker

```bash
docker compose up --build
```

Sobe o serviço, o PostgreSQL e o Redis. As migrations do Flyway criam as tabelas e os templates iniciais automaticamente.

### Sem Docker para a aplicação

```bash
docker compose up -d postgres redis    # PostgreSQL na porta 5433, Redis na 6379
set -a && source .env && set +a        # Linux/macOS (Git Bash no Windows)
./mvnw spring-boot:run
```

O serviço sobe em `http://localhost:8080`.

## Uso

Todas as rotas `/v1/**` exigem o header `X-API-Key`:

- rotas `/v1/admin/**`: chave de administrador (`IA_SERVICE_ADMIN_KEY`);
- demais rotas: API key do cliente.

### Cadastrando um cliente

```bash
curl -X POST http://localhost:8080/v1/admin/clients   -H "Content-Type: application/json"   -H "X-API-Key: <IA_SERVICE_ADMIN_KEY>"   -d '{"name": "portal-atendimento", "rateLimitPerMinute": 10, "dailyQuota": 200}'
```

```json
{
  "client": { "id": 1, "name": "portal-atendimento", "apiKeyPrefix": "dtia_Xk3p9Qa", "rateLimitPerMinute": 10, "dailyQuota": 200, "active": true, "createdAt": "..." },
  "apiKey": "dtia_Xk3p9Qa..."
}
```

A API key completa aparece **somente nesta resposta**: o serviço guarda apenas o hash (SHA-256). Use-a nos exemplos abaixo no lugar de `<API_KEY_DO_CLIENTE>`.

### Chat

```bash
curl -X POST http://localhost:8080/v1/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <API_KEY_DO_CLIENTE>" \
  -d '{
    "message": "E qual a população dela?",
    "history": [
      {"role": "user", "content": "Qual a capital do Brasil?"},
      {"role": "assistant", "content": "Brasília."}
    ]
  }'
```

O serviço não guarda histórico: o cliente envia a conversa a cada chamada.

### Streaming

```bash
curl -N -X POST http://localhost:8080/v1/chat/stream \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <API_KEY_DO_CLIENTE>" \
  -d '{"message": "Conte uma história curta."}'
```

```
event:message
data:{"content":"Era uma vez"}

event:message
data:{"content":" um gateway..."}

event:done
data:{}
```

Se o provedor falhar no meio da geração, é enviado um evento `error` em vez do `done`.

### Output estruturado

```bash
curl -X POST http://localhost:8080/v1/structured \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <API_KEY_DO_CLIENTE>" \
  -d '{
    "input": "João Silva, 32 anos, mora em Curitiba.",
    "schema": {
      "type": "object",
      "properties": {
        "nome": {"type": "string"},
        "idade": {"type": "integer"},
        "cidade": {"type": "string"}
      },
      "required": ["nome", "idade", "cidade"]
    }
  }'
```

```json
{ "data": { "nome": "João Silva", "idade": 32, "cidade": "Curitiba" }, "model": "gemini-2.5-flash", "provider": "gemini" }
```

A resposta do modelo é validada contra o schema. Se não for válida, o serviço tenta mais uma vez; persistindo o erro, retorna `422`.
Por segurança, referências externas (`$ref` para URLs) não são aceitas no schema.

### Tarefas prontas

```bash
curl -X POST http://localhost:8080/v1/tasks/resumir-texto \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <API_KEY_DO_CLIENTE>" \
  -d '{"variables": {"texto": "...", "linhas": "3"}}'
```

Use `?version=N` para uma versão específica; sem o parâmetro, é usada a versão ativa mais recente.

| Template | Variáveis | Resposta |
|---|---|---|
| `resumir-texto` | `texto`, `linhas` | texto (`content`) |
| `classificar-ticket` | `ticket` | JSON com `categoria`, `prioridade` e `resumo` (`data`) |
| `gerar-descricao-produto` | `produto`, `caracteristicas`, `tom` | texto (`content`) |

### Gestão de templates

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/v1/admin/templates` | Lista templates e versões |
| `GET` | `/v1/admin/templates/{name}` | Versões de um template |
| `POST` | `/v1/admin/templates` | Cria um template ou uma nova versão |
| `PATCH` | `/v1/admin/templates/{name}/versions/{version}` | Ativa ou desativa uma versão |

Variáveis são escritas como `{nome}` nos textos do template. Versões nunca são editadas: cada alteração cria uma nova versão.

### Clientes

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/v1/admin/clients` | Cadastra um cliente e gera a API key |
| `GET` | `/v1/admin/clients` | Lista os clientes |
| `GET` | `/v1/admin/clients/{id}` | Detalha um cliente |
| `PATCH` | `/v1/admin/clients/{id}` | Altera limites ou ativa/desativa (`{"active": false}`) |
| `POST` | `/v1/admin/clients/{id}/rotate-key` | Gera nova chave; a anterior para de funcionar |

Templates também podem pertencer a um cliente (campo `clientId` na criação). Um template do cliente com o mesmo nome de um template global o substitui para aquele cliente.

### Limites

As rotas de IA (`/v1/chat`, `/v1/structured`, `/v1/tasks`) consomem os limites do cliente:

- **por minuto**: token bucket, recarregado aos poucos ao longo do minuto;
- **cota diária**: zerada à meia-noite (UTC).

Cada resposta informa o saldo nos headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-Quota-Limit` e `X-Quota-Remaining`. Ao estourar um limite, o serviço responde `429` com o header `Retry-After` (em segundos).

### Cache

Respostas de `/v1/chat`, `/v1/structured` e `/v1/tasks` ficam em cache no Redis (padrão: 1 hora), separadas por cliente. Uma pergunta repetida é respondida sem chamar o Gemini, economizando a cota gratuita. O streaming não usa cache.

Para ignorar o cache em uma chamada, envie `Cache-Control: no-cache`.

### Uso e custo

```bash
curl http://localhost:8080/v1/usage?from=2026-09-01&to=2026-09-30 -H "X-API-Key: <API_KEY_DO_CLIENTE>"
```

```json
{
  "from": "2026-09-01", "to": "2026-09-30",
  "totals": { "calls": 42, "successfulCalls": 41, "cacheHits": 12, "promptTokens": 5310, "outputTokens": 9820, "estimatedCost": 0.026143 }
}
```

Sem datas, retorna os últimos 30 dias. `GET /v1/admin/usage` traz os mesmos totais agrupados por cliente. O custo é **estimado** com os preços do plano pago configurados em `ia-service.pricing`; no plano gratuito o custo real é zero.

### Erros

Erros seguem o formato `ProblemDetail` (RFC 9457):

| Status | Quando |
|---|---|
| `400` | Corpo inválido, schema inválido ou variáveis do template ausentes |
| `401` | Header `X-API-Key` ausente, inválido ou de cliente desativado |
| `404` | Template ou cliente inexistente |
| `409` | Cliente com nome já cadastrado |
| `422` | O modelo não gerou JSON válido para o schema |
| `429` | Limite por minuto ou cota diária atingidos |
| `502` | O provedor de IA recusou a chamada (erro que não se resolve tentando de novo) |
| `503` | O provedor de IA está indisponível (com `Retry-After` quando o circuit breaker está aberto) |

## Endpoints úteis

| Rota | Descrição |
|---|---|
| `/swagger-ui.html` | Documentação interativa da API |
| `/v3/api-docs` | Especificação OpenAPI |
| `/actuator/health` | Health check |

## Testes

```bash
./mvnw test      # testes unitários
./mvnw verify    # unitários + integração + relatório e verificação de cobertura
```

- **Unitários** (`*Test`): rodam sem Docker e sem rede.
- **Integração** (`*IT`): sobem PostgreSQL e Redis reais com Testcontainers; são ignorados quando o Docker não está disponível.
- **Resiliência de ponta a ponta** (`GeminiResilienceIT`): a API do Gemini é simulada com WireMock, e o SDK do Google faz chamadas HTTP de verdade.
- **Cobertura**: relatório em `target/site/jacoco/index.html`; o build falha abaixo de 80% de linhas.

Nenhum teste chama a API real do Gemini. O CI (GitHub Actions) roda `./mvnw verify` a cada push e pull request, e valida a imagem Docker na `main`.

## Resiliência

Cada chamada ao Gemini passa por:

1. **Timeout** por chamada (`GEMINI_TIMEOUT`).
2. **Retry** com backoff exponencial, apenas para erros transitórios (408, 429, 5xx, timeout). Erros como `400` não são repetidos.
3. **Circuit breaker** por modelo: após muitas falhas seguidas, o modelo deixa de ser chamado por 30 s, e as requisições falham na hora em vez de esperar.
4. **Modelo reserva** (`GEMINI_FALLBACK_MODEL`): se o modelo principal continuar falhando, a resposta vem do reserva, com `"fallback": true`. Respostas do reserva não vão para o cache.

O streaming usa só o modelo principal, sem novas tentativas: depois que o primeiro trecho chega ao cliente, não há como recomeçar a resposta de forma transparente.

Se o Redis ficar indisponível, o serviço continua respondendo sem cache e sem aplicar limites (*fail open*), registrando o problema no log. Cada operação no Redis tem timeout de 2 segundos.

## Documentação

Escopo, roadmap e detalhamento de cada fase em [`docs/`](docs/README.md).

> **Aviso:** no plano gratuito do Gemini, os dados enviados podem ser usados pelo Google para melhorar os modelos. Não envie dados sensíveis.

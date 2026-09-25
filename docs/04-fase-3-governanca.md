# Fase 3 — Governança

## Objetivo

Permitir que vários projetos usem o serviço com segurança e controle: cada cliente tem sua própria API key, limites de uso e registro de consumo. Adicionar cache para economizar a cota gratuita do Gemini.

## Entregas

- Cadastro de clientes (tenants) com API keys próprias.
- Rate limit por minuto e cota diária por cliente.
- Registro de uso de cada chamada (tokens, latência, custo estimado).
- Cache de respostas no Redis.
- Endpoints de consulta de uso.

## Tarefas

### Clientes e API keys
- [x] Entidade `Client`: `name`, `apiKeyHash`, `apiKeyPrefix`, `rateLimitPerMinute`, `dailyQuota`, `active`.
- [x] Geração de chave no formato `dtia_<43 caracteres>` (256 bits aleatórios); exibida **uma única vez** na criação.
- [x] Armazenar apenas o hash (SHA-256) e o prefixo (para identificação em logs e telas).
- [x] Substituir o filtro de chave fixa da fase 1 por validação no banco, com cache em memória (Caffeine, TTL 30s, inclusive para chaves inválidas).
- [x] Disponibilizar o cliente autenticado no contexto da requisição (`AuthenticatedClient`).
- [x] Endpoints admin: criar cliente, listar, detalhar, alterar limites, ativar/desativar e rotacionar chave.
- [x] Chave de administrador separada (`IA_SERVICE_ADMIN_KEY`) para `/v1/admin/**`.

### Rate limit e cota
- [x] Adicionar Redis ao `docker-compose.yml`.
- [x] Bucket4j com backend Redis (Lettuce), distribuído entre instâncias.
- [x] Limite por minuto (token bucket) e cota diária (contador por dia UTC) configuráveis por cliente.
- [x] Ao exceder: `429 Too Many Requests` com header `Retry-After`.
- [x] Headers informativos: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-Quota-Limit`, `X-Quota-Remaining`.
- [x] Aplicado só às rotas de IA (`/v1/chat`, `/v1/structured`, `/v1/tasks`), sem contar duas vezes o *dispatch* assíncrono do streaming.

### Registro de uso
- [x] Tabela `usage_record` preenchida a cada chamada ao provedor.
- [x] Capturar tokens a partir dos metadados da resposta do Spring AI, inclusive no streaming.
- [x] Custo estimado com base em tabela de preços configurável (`ia-service.pricing`).
- [x] Gravação assíncrona (`@Async` em virtual threads) para não aumentar a latência da resposta.
- [x] `GET /v1/usage?from=&to=`: consumo do próprio cliente.
- [x] `GET /v1/admin/usage`: consumo agregado por cliente.

### Templates por cliente
- [x] Templates podem ser globais ou pertencer a um cliente (`client_id` nulo = global).
- [x] Template do cliente com o mesmo nome de um global o substitui para aquele cliente.
- [x] Unicidade em (`client_id`, `name`, `version`) com `NULLS NOT DISTINCT`.

### Cache
- [x] Chave de cache: hash de (cliente + operação + provedor + system prompt + histórico + mensagem + schema).
- [x] Cache separado por cliente.
- [x] TTL configurável; streaming não usa cache; `Cache-Control: no-cache` ignora o cache na leitura.
- [x] Registrar no `usage_record` se a resposta veio do cache.

## Decisões tomadas

- **Cache e registro de uso em um decorator (`GatewayAiProvider`), não em um Advisor do Spring AI.** O decorator implementa a própria interface `AiProvider` e é `@Primary`: controllers e serviços não mudaram. Fica independente do Spring AI e vai funcionar igual para qualquer provedor na fase 6.
- **Respostas estruturadas só entram no cache se seguirem o schema.** Caso contrário, a nova tentativa do `StructuredOutputService` receberia a mesma resposta inválida do cache.
- **Cache separado por cliente**, mesmo quando a pergunta é idêntica: evita que um cliente descubra, pelo tempo de resposta, o que outro perguntou.
- **SHA-256 simples para as chaves**, sem bcrypt: a chave tem 256 bits aleatórios, então força bruta é inviável, e a validação precisa ser rápida a cada requisição.
- **Cota diária por dia do calendário (UTC)**, e não uma janela móvel de 24h: mais fácil de entender ("zera à meia-noite").
- **O limite faz parte da chave do bucket no Redis:** ao alterar o limite de um cliente, um bucket novo é criado com a configuração nova.
- **Fail open com Redis indisponível:** o serviço continua respondendo, sem cache e sem limites, e registra o problema no log. Cada operação no Redis tem timeout de 2s. Isso protege a disponibilidade, mas custa até ~4s a mais por requisição enquanto o Redis estiver fora.
- **Limites contam requisições, não chamadas ao provedor:** respostas do cache e erros do provedor também consomem limite; a nova tentativa interna do output estruturado não.
- **`temperature > 0` não desliga o cache** (previsto no planejamento original): o serviço não expõe `temperature`, e o padrão do Gemini é maior que zero, o que desligaria o cache sempre. O controle fica com o `Cache-Control: no-cache`.
- **`IA_SERVICE_API_KEY` virou `IA_SERVICE_ADMIN_KEY`:** a chave única da fase 1 passou a ser só de administrador.

## Modelo de dados

```sql
CREATE TABLE client (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL UNIQUE,
    api_key_hash          VARCHAR(64)  NOT NULL UNIQUE,
    api_key_prefix        VARCHAR(16)  NOT NULL,
    rate_limit_per_minute INT          NOT NULL,
    daily_quota           INT          NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE usage_record (
    id             BIGSERIAL PRIMARY KEY,
    client_id      BIGINT        NOT NULL REFERENCES client (id),
    endpoint       VARCHAR(200)  NOT NULL,
    operation      VARCHAR(20)   NOT NULL,   -- chat | stream | structured
    provider       VARCHAR(30)   NOT NULL,
    model          VARCHAR(100),
    prompt_tokens  INT,
    output_tokens  INT,
    latency_ms     INT           NOT NULL,
    estimated_cost NUMERIC(12, 6),
    cache_hit      BOOLEAN       NOT NULL DEFAULT FALSE,
    success        BOOLEAN       NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_usage_client_date ON usage_record (client_id, created_at);

ALTER TABLE prompt_template ADD COLUMN client_id BIGINT REFERENCES client (id);
-- unicidade passa a ser (client_id, name, version) com NULLS NOT DISTINCT
```

## Critérios de aceite

- [x] Dois clientes diferentes usam o serviço com chaves distintas e têm uso separado.
- [x] Ultrapassar o limite por minuto retorna `429` com `Retry-After`.
- [x] Chave desativada ou rotacionada deixa de funcionar imediatamente nesta instância (demais instâncias respeitam o TTL de 30s do cache de chaves).
- [x] Requisição repetida é respondida do cache, sem chamar o Gemini.
- [x] `/v1/usage` retorna totais de requisições e tokens no período.
- [x] Nenhuma API key é gravada em texto puro no banco ou nos logs.

## Status

Implementada. 81 testes automatizados passando, incluindo integração com PostgreSQL e Redis reais (Testcontainers) para ciclo de vida de clientes, rate limit, cota, cache, registro de uso e templates por cliente.
Validado com `docker compose up`: cadastro de cliente, `429` com `Retry-After` no limite por minuto, uso registrado e consultado, chaves no Redis e *fail open* com o Redis parado.
**Validado com a API real do Gemini na fase 6** (25/09/2026), com os modelos atualizados para `gemini-3.8-flash` e `gemini-3.5-flash-lite` — veja a [fase 6](07-fase-6-extra.md#validação-com-a-api-real-do-gemini).

## Fora desta fase

Retry/circuit breaker e separação de testes unitários e de integração (Surefire/Failsafe) ficam para a fase 4.

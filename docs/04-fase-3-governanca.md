# Fase 3 — Governança

## Objetivo

Permitir que vários projetos usem o serviço com segurança e controle: cada cliente tem sua própria API key, limites de uso e registro de consumo. Adicionar cache para economizar a cota gratuita do Gemini.

## Entregas

- Cadastro de clientes (tenants) com API keys próprias.
- Rate limit por minuto e cota diária por cliente.
- Registro de uso de cada requisição (tokens, latência, custo estimado).
- Cache de respostas no Redis.
- Endpoints de consulta de uso.

## Tarefas

### Clientes e API keys
- [ ] Entidade `Client`: `name`, `apiKeyHash`, `apiKeyPrefix`, `rateLimitPerMinute`, `dailyQuota`, `active`.
- [ ] Geração de chave no formato `dtia_<aleatório>`; exibida **uma única vez** na criação.
- [ ] Armazenar apenas o hash (SHA-256) e o prefixo (para identificação em logs).
- [ ] Substituir o filtro de chave fixa da fase 1 por validação no banco (com cache em memória).
- [ ] Disponibilizar o cliente autenticado no contexto da requisição.
- [ ] Endpoints admin: criar cliente, rotacionar chave, desativar cliente.
- [ ] Chave de administrador separada para `/v1/admin/**`.

### Rate limit e cota
- [ ] Adicionar Redis ao `docker-compose.yml`.
- [ ] Bucket4j com backend Redis (distribuído, funciona com várias instâncias).
- [ ] Limite por minuto e cota diária configuráveis por cliente.
- [ ] Ao exceder: `429 Too Many Requests` com header `Retry-After`.
- [ ] Headers informativos: `X-RateLimit-Remaining`, `X-Quota-Remaining`.

### Registro de uso
- [ ] Tabela `usage_record` preenchida a cada chamada ao provedor.
- [ ] Capturar tokens a partir dos metadados da resposta do Spring AI (`ChatResponse.getMetadata().getUsage()`).
- [ ] Custo estimado com base em tabela de preços configurável (no plano gratuito o custo real é zero; o valor serve para métricas).
- [ ] Gravação assíncrona para não aumentar a latência da resposta.
- [ ] `GET /v1/usage?from=&to=`: consumo do próprio cliente.
- [ ] `GET /v1/admin/usage`: consumo agregado por cliente.

### Templates por cliente
- [ ] Templates podem ser globais ou pertencer a um cliente (`client_id` nulo = global).

### Cache
- [ ] Chave de cache: hash de (modelo + system prompt + mensagens + parâmetros).
- [ ] Implementar como um *Advisor* do Spring AI.
- [ ] TTL configurável; cache desativado para streaming e para requisições com `temperature > 0`, ou controlado pelo header `Cache-Control: no-cache`.
- [ ] Registrar no `usage_record` se a resposta veio do cache.

## Modelo de dados

```sql
CREATE TABLE client (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL UNIQUE,
    api_key_hash          VARCHAR(64)  NOT NULL UNIQUE,
    api_key_prefix        VARCHAR(16)  NOT NULL,
    rate_limit_per_minute INT          NOT NULL DEFAULT 10,
    daily_quota           INT          NOT NULL DEFAULT 200,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE usage_record (
    id             BIGSERIAL PRIMARY KEY,
    client_id      BIGINT       NOT NULL REFERENCES client(id),
    endpoint       VARCHAR(100) NOT NULL,
    provider       VARCHAR(30)  NOT NULL,
    model          VARCHAR(60)  NOT NULL,
    prompt_tokens  INT,
    output_tokens  INT,
    latency_ms     INT          NOT NULL,
    estimated_cost NUMERIC(12,6),
    cache_hit      BOOLEAN      NOT NULL DEFAULT FALSE,
    success        BOOLEAN      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_usage_client_date ON usage_record (client_id, created_at);
```

## Critérios de aceite

- [ ] Dois clientes diferentes usam o serviço com chaves distintas e têm uso separado.
- [ ] Ultrapassar o limite por minuto retorna `429` com `Retry-After`.
- [ ] Chave desativada ou rotacionada deixa de funcionar imediatamente (respeitando o TTL do cache de chaves).
- [ ] Requisição repetida é respondida do cache, sem chamar o Gemini.
- [ ] `/v1/usage` retorna totais de requisições e tokens no período.
- [ ] Nenhuma API key é gravada em texto puro no banco ou nos logs.

## Fora desta fase

Retry/circuit breaker e testes automatizados de integração (fase 4).

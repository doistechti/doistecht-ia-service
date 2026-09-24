# Fase 2 — Produto

## Objetivo

Transformar o serviço em algo útil para os projetos clientes: respostas em streaming, respostas estruturadas em JSON e tarefas prontas baseadas em templates de prompt versionados.

## Entregas

- Endpoint `POST /v1/chat/stream` com Server-Sent Events.
- Suporte a histórico de mensagens no `/v1/chat`.
- Endpoint `POST /v1/structured` com resposta validada contra um JSON Schema.
- PostgreSQL + Flyway.
- Registro de templates de prompt versionados.
- Endpoint `POST /v1/tasks/{template}`.

## Tarefas

### Streaming
- [ ] Adicionar `chatStream(ChatCommand)` na interface `AiProvider` retornando `Flux<String>`.
- [ ] Implementar com `chatClient.prompt()...stream().content()`.
- [ ] Controller retornando `text/event-stream`.
- [ ] Tratar cancelamento do cliente (conexão fechada no meio do stream).

### Histórico de conversa
- [ ] Aceitar lista `messages` (`role`: `user` | `assistant`) na requisição.
- [ ] Sem persistência: o cliente envia o histórico a cada chamada.

### Output estruturado
- [ ] Requisição recebe o texto de entrada + JSON Schema desejado.
- [ ] Usar o suporte a structured output do Spring AI.
- [ ] Validar a resposta contra o schema; em caso de falha, retentar uma vez e depois retornar `422`.

### Banco de dados
- [ ] Adicionar PostgreSQL ao `docker-compose.yml`.
- [ ] Dependências: `spring-boot-starter-data-jpa`, `postgresql`, `flyway-core`, `flyway-database-postgresql`.
- [ ] Migration inicial da tabela `prompt_template`.

### Templates de prompt
- [ ] Entidade `PromptTemplate`: `name`, `version`, `systemPrompt`, `userPromptTemplate`, `outputSchema` (opcional), `active`, `createdAt`.
- [ ] Unicidade em (`name`, `version`).
- [ ] Renderização de variáveis com o `PromptTemplate` do Spring AI (`{variavel}`).
- [ ] Validação: todas as variáveis do template precisam ser enviadas.
- [ ] Seeds via Flyway: `resumir-texto@v1`, `classificar-ticket@v1`, `gerar-descricao-produto@v1`.
- [ ] CRUD administrativo básico: `GET/POST /v1/admin/templates`.

## Contratos da API

**Tarefa por template**
```http
POST /v1/tasks/resumir-texto?version=1
X-API-Key: <chave>

{
  "variables": {
    "texto": "…",
    "linhas": "3"
  }
}
```
Sem `version`, usa a versão ativa mais recente.

**Output estruturado**
```http
POST /v1/structured
X-API-Key: <chave>

{
  "input": "João Silva, 32 anos, mora em Curitiba.",
  "schema": {
    "type": "object",
    "properties": {
      "nome": { "type": "string" },
      "idade": { "type": "integer" },
      "cidade": { "type": "string" }
    },
    "required": ["nome", "idade", "cidade"]
  }
}
```

## Modelo de dados

```sql
CREATE TABLE prompt_template (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(100) NOT NULL,
    version              INT          NOT NULL,
    system_prompt        TEXT,
    user_prompt_template TEXT         NOT NULL,
    output_schema        JSONB,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (name, version)
);
```

## Critérios de aceite

- [ ] `/v1/chat/stream` entrega tokens progressivamente (verificável com `curl -N`).
- [ ] `/v1/structured` retorna JSON válido conforme o schema enviado.
- [ ] `/v1/tasks/resumir-texto` funciona apenas com as variáveis, sem o cliente enviar prompt.
- [ ] Template inexistente retorna `404`; variável faltando retorna `400`.
- [ ] Migrations rodam automaticamente na subida.

## Fora desta fase

Isolamento por cliente e controle de uso (fase 3).

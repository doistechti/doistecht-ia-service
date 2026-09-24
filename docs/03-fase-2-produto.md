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
- [x] Adicionar `chatStream(ChatCommand)` na interface `AiProvider` retornando `Flux<String>`.
- [x] Implementar com `chatClient.prompt()...stream().content()`.
- [x] Controller retornando `text/event-stream`.
- [x] Tratar falha do provedor no meio do stream (evento `error`).
- [x] Cancelamento do cliente: a assinatura do `Flux` é cancelada pelo Spring MVC quando a conexão fecha.

### Histórico de conversa
- [x] Aceitar lista `history` (`role`: `user` | `assistant`) na requisição, com no máximo 50 mensagens.
- [x] Sem persistência: o cliente envia o histórico a cada chamada.

### Output estruturado
- [x] Requisição recebe o texto de entrada + JSON Schema desejado.
- [x] Enviar o schema ao Gemini via `GoogleGenAiChatOptions.outputSchema(...)` (`responseJsonSchema` + `application/json`).
- [x] Validar a resposta contra o schema (networknt json-schema-validator); em caso de falha, retentar uma vez e depois retornar `422`.
- [x] Bloquear `$ref` externos no schema enviado pelo cliente (evita SSRF).

### Banco de dados
- [x] Adicionar PostgreSQL ao `docker-compose.yml` (porta 5433 no host).
- [x] Dependências: `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `postgresql`.
- [x] Migration inicial da tabela `prompt_template`.
- [x] Hibernate em modo `validate`: o schema é responsabilidade exclusiva do Flyway.

### Templates de prompt
- [x] Entidade `PromptTemplate`: `name`, `version`, `systemPrompt`, `userPromptTemplate`, `outputSchema` (opcional), `active`, `createdAt`.
- [x] Unicidade em (`name`, `version`).
- [x] Renderização de variáveis `{variavel}` com o `TemplateRenderer` próprio.
- [x] Validação: todas as variáveis do template precisam ser enviadas; a resposta lista todas as ausentes.
- [x] Seeds via Flyway: `resumir-texto@v1`, `classificar-ticket@v1`, `gerar-descricao-produto@v1`.
- [x] Gestão administrativa: `GET/POST /v1/admin/templates`, `GET /v1/admin/templates/{name}` e `PATCH /v1/admin/templates/{name}/versions/{version}`.

## Decisões tomadas

- **Renderizador de templates próprio em vez do `PromptTemplate` do Spring AI:** mantém o Spring AI restrito ao pacote `provider`, permite listar todas as variáveis ausentes de uma vez e faz a substituição em uma única passada (chaves dentro dos valores não são interpretadas).
- **Chunks do streaming em JSON (`{"content": "..."}`):** quebras de linha no texto gerado não quebram o formato SSE.
- **Versões imutáveis:** criar um template com nome existente gera a próxima versão; versões antigas só podem ser ativadas ou desativadas.
- **Validação do schema do lado do serviço, mesmo com o Gemini suportando JSON Schema:** garante o contrato independente do provedor, o que será importante na fase 6.

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
Sem `version`, usa a versão ativa mais recente. Templates com `outputSchema` retornam o campo `data` (JSON); os demais retornam `content` (texto).

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

**Streaming**
```
event:message
data:{"content":"Olá"}

event:done
data:{}
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
    CONSTRAINT uk_prompt_template_name_version UNIQUE (name, version)
);
```

## Critérios de aceite

- [x] `/v1/chat/stream` entrega tokens progressivamente (verificável com `curl -N`).
- [x] `/v1/structured` retorna JSON válido conforme o schema enviado.
- [x] `/v1/tasks/resumir-texto` funciona apenas com as variáveis, sem o cliente enviar prompt.
- [x] Template inexistente retorna `404`; variável faltando retorna `400`.
- [x] Migrations rodam automaticamente na subida.

## Status

Implementada. 41 testes automatizados passando, incluindo testes de integração com PostgreSQL real (Testcontainers) cobrindo migrations, seeds, versionamento, ativação/desativação e execução de tarefas.
Validado com `docker compose up`: serviço + PostgreSQL, seeds carregados e respostas de erro (`400`, `404`, `502`, evento `error` no streaming) conferidas.
**Pendente:** validar streaming e output estruturado com chave real do Gemini.

## Fora desta fase

Isolamento por cliente e controle de uso (fase 3).

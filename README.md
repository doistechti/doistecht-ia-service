# doistecht-ia-service

AI Gateway em Java que disponibiliza modelos de IA para diversos projetos por meio de uma API única.

Os projetos clientes não chamam o provedor de IA diretamente: eles consomem este serviço, que centraliza autenticação, controle de uso, prompts e observabilidade.

> **Status:** Fase 2 (Produto) concluída — veja o [roadmap](docs/01-escopo-do-projeto.md#5-roadmap).

## Funcionalidades

- **Chat** com histórico de conversa: `POST /v1/chat`
- **Streaming** da resposta via Server-Sent Events: `POST /v1/chat/stream`
- **Output estruturado**: JSON validado contra um JSON Schema: `POST /v1/structured`
- **Tarefas prontas** a partir de templates de prompt versionados: `POST /v1/tasks/{template}`
- **Gestão de templates**: `/v1/admin/templates`

## Stack

- Java 21 (virtual threads)
- Spring Boot 4.1 + Spring AI 2.0
- Gemini (Google AI Studio)
- PostgreSQL 17 + Flyway + Spring Data JPA
- Maven
- springdoc-openapi (Swagger UI)
- JUnit 5, Mockito, Testcontainers
- Docker / Docker Compose

## Como executar

### Pré-requisitos

- Chave gratuita do Gemini: [Google AI Studio](https://aistudio.google.com/apikey)
- Docker **ou** Java 21 + Maven (neste caso, Docker ainda é usado para o PostgreSQL)

### Configuração

```bash
cp .env.example .env
# edite o .env e preencha GEMINI_API_KEY e IA_SERVICE_API_KEY
```

| Variável | Obrigatória | Descrição |
|---|---|---|
| `GEMINI_API_KEY` | Sim | Chave do Google AI Studio |
| `GEMINI_MODEL` | Não | Modelo Gemini (padrão `gemini-2.5-flash`) |
| `IA_SERVICE_API_KEY` | Sim | Chave que os clientes enviam no header `X-API-Key` |
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | Não | Credenciais do PostgreSQL (padrão `ia_service`) |
| `DB_URL` | Não | URL JDBC (padrão `jdbc:postgresql://localhost:5433/ia_service`) |

### Com Docker

```bash
docker compose up --build
```

Sobe o serviço e o PostgreSQL. As migrations do Flyway criam as tabelas e os templates iniciais automaticamente.

### Sem Docker para a aplicação

```bash
docker compose up -d postgres          # PostgreSQL publicado na porta 5433
set -a && source .env && set +a        # Linux/macOS (Git Bash no Windows)
./mvnw spring-boot:run
```

O serviço sobe em `http://localhost:8080`.

## Uso

Todas as rotas `/v1/**` exigem o header `X-API-Key`.

### Chat

```bash
curl -X POST http://localhost:8080/v1/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <IA_SERVICE_API_KEY>" \
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
  -H "X-API-Key: <IA_SERVICE_API_KEY>" \
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
  -H "X-API-Key: <IA_SERVICE_API_KEY>" \
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
  -H "X-API-Key: <IA_SERVICE_API_KEY>" \
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

### Erros

Erros seguem o formato `ProblemDetail` (RFC 9457):

| Status | Quando |
|---|---|
| `400` | Corpo inválido, schema inválido ou variáveis do template ausentes |
| `401` | Header `X-API-Key` ausente ou inválido |
| `404` | Template inexistente ou sem versão ativa |
| `422` | O modelo não gerou JSON válido para o schema |
| `502` | Falha ao obter resposta do provedor de IA |

## Endpoints úteis

| Rota | Descrição |
|---|---|
| `/swagger-ui.html` | Documentação interativa da API |
| `/v3/api-docs` | Especificação OpenAPI |
| `/actuator/health` | Health check |

## Testes

```bash
./mvnw test
```

Os testes de integração sobem um PostgreSQL real com Testcontainers e são ignorados quando o Docker não está disponível. Nenhum teste chama a API real do Gemini.

## Documentação

Escopo, roadmap e detalhamento de cada fase em [`docs/`](docs/README.md).

> **Aviso:** no plano gratuito do Gemini, os dados enviados podem ser usados pelo Google para melhorar os modelos. Não envie dados sensíveis.

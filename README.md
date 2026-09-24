# doistecht-ia-service

AI Gateway em Java que disponibiliza modelos de IA para diversos projetos por meio de uma API única.

Os projetos clientes não chamam o provedor de IA diretamente: eles consomem este serviço, que centraliza autenticação, controle de uso, prompts e observabilidade.

> **Status:** Fase 1 (MVP) — veja o [roadmap](docs/01-escopo-do-projeto.md#5-roadmap).

## Stack

- Java 21 (virtual threads)
- Spring Boot 4.1 + Spring AI 2.0
- Gemini (Google AI Studio)
- Maven
- springdoc-openapi (Swagger UI)
- Docker / Docker Compose

## Como executar

### Pré-requisitos

- Chave gratuita do Gemini: [Google AI Studio](https://aistudio.google.com/apikey)
- Docker **ou** Java 21 + Maven

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

### Com Docker

```bash
docker compose up --build
```

### Sem Docker

```bash
# Linux/macOS (Git Bash no Windows)
set -a && source .env && set +a
./mvnw spring-boot:run
```

O serviço sobe em `http://localhost:8080`.

## Uso

```bash
curl -X POST http://localhost:8080/v1/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <IA_SERVICE_API_KEY>" \
  -d '{"message": "Explique o que é um AI Gateway em uma frase.", "systemPrompt": "Responda de forma objetiva."}'
```

```json
{
  "content": "Um AI Gateway é uma camada intermediária que ...",
  "model": "gemini-2.5-flash",
  "provider": "gemini"
}
```

Erros seguem o formato `ProblemDetail` (RFC 9457):

| Status | Quando |
|---|---|
| `400` | Corpo inválido (ex.: `message` vazia) |
| `401` | Header `X-API-Key` ausente ou inválido |
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

## Documentação

Escopo, roadmap e detalhamento de cada fase em [`docs/`](docs/README.md).

> **Aviso:** no plano gratuito do Gemini, os dados enviados podem ser usados pelo Google para melhorar os modelos. Não envie dados sensíveis.

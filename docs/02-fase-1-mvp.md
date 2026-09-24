# Fase 1 — MVP

## Objetivo

Colocar no ar a primeira versão funcional do serviço: uma API Spring Boot que recebe uma mensagem, envia ao Gemini e devolve a resposta, protegida por uma API key simples e rodando em Docker.

## Entregas

- Projeto Spring Boot com Maven.
- Endpoint `POST /v1/chat` integrado ao Gemini via Spring AI.
- Interface `AiProvider` isolando a biblioteca de IA.
- Autenticação por API key fixa (configurada por variável de ambiente).
- Tratamento padronizado de erros.
- Documentação com Swagger UI.
- Dockerfile e `docker-compose.yml`.
- README na raiz com instruções de execução.

## Tarefas

### Setup do projeto
- [x] Gerar projeto no [start.spring.io](https://start.spring.io): Maven, Java 21, Spring Boot 4.1 + Spring AI 2.0.
- [x] Dependências: `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `springdoc-openapi-starter-webmvc-ui`.
- [x] Adicionar o BOM do Spring AI (`spring-ai-bom`) no `dependencyManagement`.
- [x] Adicionar o starter do Gemini via Google GenAI (`spring-ai-starter-model-google-genai`), que aceita a chave do Google AI Studio.
- [x] Habilitar virtual threads: `spring.threads.virtual.enabled=true`.

### Integração com IA
- [x] Criar a interface `AiProvider` com o método `chat(ChatCommand)`.
- [x] Implementar `GeminiProvider` usando o `ChatClient` do Spring AI.
- [x] Configurar modelo e chave via `application.yml` + variáveis de ambiente (nunca commitar a chave).

### API
- [x] `ChatController` com `POST /v1/chat`.
- [x] DTOs de entrada/saída com Bean Validation.
- [x] `GlobalExceptionHandler` retornando erros no formato `ProblemDetail` (RFC 9457).

### Segurança
- [x] Filtro que valida o header `X-API-Key` contra a variável `IA_SERVICE_API_KEY`.
- [x] Liberar `/swagger-ui/**`, `/v3/api-docs/**` e `/actuator/health` sem chave.

### Infra
- [x] `Dockerfile` multi-stage (build com Maven, runtime com JRE 21).
- [x] `docker-compose.yml` com o serviço e o arquivo `.env`.
- [x] `.env.example` documentando as variáveis.
- [x] Adicionar `.env` e `.idea/` ao `.gitignore`.

## Estrutura de pacotes

```
br.com.doistecht.iaservice
├── api
│   ├── controller      # ChatController
│   └── dto             # ChatRequest, ChatResponse
├── provider
│   ├── AiProvider      # interface
│   └── gemini          # GeminiProvider
├── security            # ApiKeyFilter
├── config              # beans e propriedades
└── exception           # GlobalExceptionHandler
```

## Contrato da API

**Requisição**
```http
POST /v1/chat
X-API-Key: <chave>
Content-Type: application/json

{
  "message": "Explique o que é um AI Gateway em uma frase.",
  "systemPrompt": "Responda de forma objetiva."
}
```

**Resposta**
```json
{
  "content": "Um AI Gateway é uma camada intermediária que ...",
  "model": "gemini-...",
  "provider": "gemini"
}
```

## Configuração

```yaml
spring:
  threads:
    virtual:
      enabled: true
  ai:
    google:
      genai:
        api-key: ${GEMINI_API_KEY}
        chat:
          model: ${GEMINI_MODEL:gemini-2.5-flash}

ia-service:
  api-key: ${IA_SERVICE_API_KEY}
```

> No Spring AI 2.0 o modelo é definido em `spring.ai.google.genai.chat.model` (a antiga `chat.options.model` está depreciada).

## Critérios de aceite

- [ ] `docker compose up` sobe o serviço sem passos manuais além do `.env`.
- [ ] `POST /v1/chat` com chave válida retorna a resposta do Gemini.
- [x] Requisição sem chave ou com chave inválida retorna `401`.
- [x] Requisição com `message` vazia retorna `400` em formato `ProblemDetail`.
- [x] Swagger UI acessível em `/swagger-ui.html`.
- [x] Nenhuma chave commitada no repositório.

## Status

Implementada. Todas as tarefas concluídas e 6 testes automatizados passando (`ChatControllerTest` + carga do contexto).
Validado localmente com o jar: health, Swagger, `401`, `400` com a lista de campos inválidos, e `502` quando o Gemini recusa a chamada.
**Pendente:** validar o `docker compose up` e uma chamada com chave real do Gemini.

## Fora desta fase

Streaming, banco de dados, multi-tenant, rate limit e testes de integração (entram nas fases seguintes).

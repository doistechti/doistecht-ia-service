# Guia da API — doistecht-ia-service

Referência de uso de todas as rotas, com exemplos em `curl`. A documentação interativa (Swagger) fica em `http://localhost:8080/swagger-ui.html`, e o arquivo [`requests.http`](../requests.http) tem as mesmas chamadas prontas para o cliente HTTP do IntelliJ ou do VS Code.


Todas as rotas `/v1/**` exigem o header `X-API-Key`:

- rotas `/v1/admin/**`: chave de administrador (`IA_SERVICE_ADMIN_KEY`);
- demais rotas: API key do cliente.

## Cadastrando um cliente

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

## Chat

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

## Streaming

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

## Output estruturado

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
{ "data": { "nome": "João Silva", "idade": 32, "cidade": "Curitiba" }, "model": "gemini-3.8-flash", "provider": "gemini" }
```

A resposta do modelo é validada contra o schema. Se não for válida, o serviço tenta mais uma vez; persistindo o erro, retorna `422`.
Por segurança, referências externas (`$ref` para URLs) não são aceitas no schema.

## Tarefas prontas

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

## Gestão de templates

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/v1/admin/templates` | Lista templates e versões |
| `GET` | `/v1/admin/templates/{name}` | Versões de um template |
| `POST` | `/v1/admin/templates` | Cria um template ou uma nova versão |
| `PATCH` | `/v1/admin/templates/{name}/versions/{version}` | Ativa ou desativa uma versão |

Variáveis são escritas como `{nome}` nos textos do template. Versões nunca são editadas: cada alteração cria uma nova versão.

## Clientes

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/v1/admin/clients` | Cadastra um cliente e gera a API key |
| `GET` | `/v1/admin/clients` | Lista os clientes |
| `GET` | `/v1/admin/clients/{id}` | Detalha um cliente |
| `PATCH` | `/v1/admin/clients/{id}` | Altera limites ou ativa/desativa (`{"active": false}`) |
| `POST` | `/v1/admin/clients/{id}/rotate-key` | Gera nova chave; a anterior para de funcionar |

Templates também podem pertencer a um cliente (campo `clientId` na criação). Um template do cliente com o mesmo nome de um template global o substitui para aquele cliente.

## Limites

As rotas de IA (`/v1/chat`, `/v1/structured`, `/v1/tasks`) consomem os limites do cliente:

- **por minuto**: token bucket, recarregado aos poucos ao longo do minuto;
- **cota diária**: zerada à meia-noite (UTC).

Cada resposta informa o saldo nos headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-Quota-Limit` e `X-Quota-Remaining`. Ao estourar um limite, o serviço responde `429` com o header `Retry-After` (em segundos).

## Cache

Respostas de `/v1/chat`, `/v1/structured` e `/v1/tasks` ficam em cache no Redis (padrão: 1 hora), separadas por cliente. Uma pergunta repetida é respondida sem chamar o Gemini, economizando a cota gratuita. O streaming não usa cache.

Para ignorar o cache em uma chamada, envie `Cache-Control: no-cache`.

## Uso e custo

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

## Documentos e RAG

Envie documentos (PDF, TXT ou MD, até 10 MB) e faça perguntas sobre eles. Cada cliente só enxerga os próprios documentos.

```bash
# 1. Enviar: o processamento é assíncrono e o documento volta com status PROCESSING
curl -X POST http://localhost:8080/v1/documents   -H "X-API-Key: <API_KEY_DO_CLIENTE>"   -F "file=@politica-de-reembolso.pdf"

# 2. Acompanhar até o status ficar READY
curl http://localhost:8080/v1/documents/1 -H "X-API-Key: <API_KEY_DO_CLIENTE>"

# 3. Perguntar
curl -X POST http://localhost:8080/v1/rag/ask   -H "Content-Type: application/json"   -H "X-API-Key: <API_KEY_DO_CLIENTE>"   -d '{"question": "Qual o prazo de reembolso?"}'
```

```json
{
  "answer": "O valor é devolvido em até 7 dias úteis [1].",
  "found": true,
  "sources": [
    { "documentId": 1, "fileName": "politica-de-reembolso.pdf", "chunkIndex": 3, "score": 0.81, "excerpt": "O valor é devolvido em até 7 dias úteis..." }
  ],
  "model": "gemini-3.8-flash",
  "provider": "gemini",
  "fallback": false
}
```

Quando nenhum trecho é relevante, a resposta vem com `"found": false` e *"Não encontrei essa informação nos documentos."*, sem chamar o modelo de chat. Use `documentIds` para restringir a busca e `topK` para a quantidade de trechos (padrão 4).

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/v1/documents` | Envia um documento (multipart, campo `file`) |
| `GET` | `/v1/documents` | Lista os documentos do cliente |
| `GET` | `/v1/documents/{id}` | Status do processamento (`PROCESSING`, `READY` ou `FAILED`) |
| `DELETE` | `/v1/documents/{id}` | Apaga o documento e seus trechos |
| `POST` | `/v1/rag/ask` | Pergunta sobre os documentos |
| `POST` | `/v1/embeddings` | Gera embeddings (`purpose`: `document` ou `query`) |

Consultar e apagar documentos não consome o limite de requisições; só as chamadas `POST` que usam o modelo consomem.

## Observabilidade

O dashboard **doistecht-ia-service** abre direto no Grafana (`http://localhost:3000`), com chamadas e tokens por cliente, taxa de erro e de cache, latência (p50/p95/p99), estado dos circuit breakers, requisições recusadas por limite e uso do modelo reserva.

Métricas próprias expostas em `/actuator/prometheus` (porta 8081):

| Métrica | Tags |
|---|---|
| `ia_gateway_calls_total` | `client`, `operation`, `provider`, `outcome` (`success`/`failure`/`cache_hit`), `fallback` |
| `ia_gateway_tokens_total` | `client`, `operation`, `type` (`input`/`output`) |
| `ia_gateway_latency_seconds` | `operation`, `provider`, `outcome` (histograma) |
| `ia_ratelimit_rejected_total` | `client`, `limit` |

Além delas, o Resilience4j publica o estado dos circuit breakers e o Spring Boot publica as métricas HTTP e da JVM.

## Erros

Erros seguem o formato `ProblemDetail` (RFC 9457):

| Status | Quando |
|---|---|
| `400` | Corpo inválido, schema inválido, variáveis do template ausentes ou documento em formato não suportado |
| `401` | Header `X-API-Key` ausente, inválido ou de cliente desativado |
| `404` | Template, cliente ou documento inexistente |
| `409` | Cliente com nome já cadastrado |
| `422` | O modelo não gerou JSON válido para o schema |
| `429` | Limite por minuto ou cota diária atingidos |
| `502` | O provedor de IA recusou a chamada (erro que não se resolve tentando de novo) |
| `503` | O provedor de IA está indisponível (com `Retry-After` quando o circuit breaker está aberto) |

## Escolhendo o provedor de IA

As rotas de chat, streaming, output estruturado, tarefas, RAG e embeddings aceitam o campo opcional `provider` (`gemini` ou `ollama`). A escolha segue esta ordem:

1. o `provider` enviado na requisição;
2. o provedor padrão do cliente (`defaultProvider`, definido pelo admin);
3. o padrão global (`AI_DEFAULT_PROVIDER`).

Sem `provider` na requisição, se o provedor escolhido estiver fora do ar, o gateway usa o reserva (`AI_FALLBACK_PROVIDER`) e a resposta vem com `"fallback": true`. Com `provider` explícito, a escolha é respeitada: não há troca silenciosa.

```bash
curl -X POST http://localhost:8080/v1/chat   -H "Content-Type: application/json"   -H "X-API-Key: <API_KEY_DO_CLIENTE>"   -d '{"message": "Olá!", "provider": "ollama"}'

# Provedores habilitados, modelos e situação de cada um
curl http://localhost:8080/v1/providers -H "X-API-Key: <API_KEY_DO_CLIENTE>"

# Provedor padrão de um cliente (texto vazio volta ao padrão global)
curl -X PATCH http://localhost:8080/v1/admin/clients/1   -H "Content-Type: application/json"   -H "X-API-Key: <IA_SERVICE_ADMIN_KEY>"   -d '{"defaultProvider": "ollama"}'
```

Os embeddings dos documentos usam sempre o provedor configurado em `RAG_EMBEDDING_PROVIDER`: vetores de modelos diferentes não são comparáveis. Trocar esse provedor exige reenviar os documentos.

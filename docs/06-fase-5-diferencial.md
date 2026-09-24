# Fase 5 — Diferencial

## Objetivo

Adicionar as funcionalidades que destacam o projeto no portfólio: busca semântica e perguntas sobre documentos (RAG) com pgvector, e um dashboard de observabilidade mostrando o uso do serviço em tempo real.

## Entregas

- Endpoint `POST /v1/embeddings`.
- Ingestão de documentos com geração de embeddings e armazenamento no pgvector.
- Endpoint `POST /v1/rag/ask` com respostas baseadas nos documentos do cliente.
- Métricas no Prometheus e dashboard no Grafana.

## Tarefas

### Embeddings
- [ ] Adicionar `embed(List<String>)` na interface `AiProvider`.
- [ ] Implementar com o modelo de embeddings do Gemini via Spring AI.
- [ ] `POST /v1/embeddings` retornando os vetores e a dimensão.

### Ingestão de documentos
- [ ] Trocar a imagem do PostgreSQL por uma com a extensão pgvector (`pgvector/pgvector`).
- [ ] Dependência `spring-ai-starter-vector-store-pgvector`.
- [ ] `POST /v1/documents` (multipart): aceitar PDF, TXT e Markdown.
- [ ] Leitura com os *DocumentReaders* do Spring AI (Tika ou PDF reader).
- [ ] Divisão em trechos com `TokenTextSplitter` (tamanho e sobreposição configuráveis).
- [ ] Gravar metadados em cada trecho: `clientId`, `documentId`, `fileName`, `chunkIndex`.
- [ ] Processamento assíncrono com status (`PROCESSING`, `READY`, `FAILED`).
- [ ] `GET /v1/documents` e `DELETE /v1/documents/{id}` (remove também os vetores).
- [ ] Respeitar os limites do plano gratuito: processar embeddings em lotes com pausa entre eles.

### RAG
- [ ] `POST /v1/rag/ask` recebendo a pergunta e, opcionalmente, filtros de documento.
- [ ] Usar o `QuestionAnswerAdvisor` (ou equivalente) do Spring AI.
- [ ] **Isolamento por cliente**: filtro obrigatório por `clientId` na busca vetorial.
- [ ] Parâmetros configuráveis: `topK` e limiar de similaridade.
- [ ] Retornar as **fontes** usadas (documento, trecho e score).
- [ ] Instruir o modelo a responder "não encontrei nos documentos" quando não houver contexto relevante.
- [ ] Contabilizar embeddings e chamadas de RAG no `usage_record`.

### Observabilidade
- [ ] Dependência `micrometer-registry-prometheus`; expor `/actuator/prometheus`.
- [ ] Aproveitar as métricas nativas do Spring AI (tokens, latência por modelo).
- [ ] Métricas próprias com tag `client`: requisições, tokens, cache hit, fallback, rate limit excedido.
- [ ] Adicionar Prometheus e Grafana ao `docker-compose.yml`.
- [ ] Provisionar datasource e dashboard do Grafana via arquivos no repositório.
- [ ] Painéis: requisições/min por cliente, tokens por cliente, latência p50/p95/p99, taxa de erro, taxa de cache hit, estado do circuit breaker.
- [ ] Proteger `/actuator/prometheus` (rede interna ou autenticação).

## Contrato da API

```http
POST /v1/rag/ask
X-API-Key: <chave>

{
  "question": "Qual o prazo de reembolso?",
  "topK": 4
}
```

```json
{
  "answer": "O prazo de reembolso é de 7 dias úteis ...",
  "sources": [
    { "documentId": 12, "fileName": "politica.pdf", "chunkIndex": 3, "score": 0.87 }
  ]
}
```

## Critérios de aceite

- [ ] Upload de um PDF deixa o documento com status `READY`.
- [ ] Pergunta sobre o conteúdo retorna resposta correta com as fontes.
- [ ] Um cliente nunca recebe trechos de documentos de outro cliente (teste automatizado).
- [ ] Pergunta fora do conteúdo retorna que não encontrou a informação.
- [ ] `docker compose up` sobe o Grafana com o dashboard já configurado.
- [ ] Dashboard mostra dados reais após algumas requisições.

## Fora desta fase

Segundo provedor de IA (fase 6).

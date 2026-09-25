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
- [x] Adicionar `embed(List<String>, EmbeddingPurpose)` na interface `AiProvider`.
- [x] Implementar com o `gemini-embedding-001` (768 dimensões, vetores normalizados), usando o tipo de tarefa `RETRIEVAL_DOCUMENT` ou `RETRIEVAL_QUERY`.
- [x] `POST /v1/embeddings` retornando os vetores e a dimensão.
- [x] Retry e circuit breaker do `ModelFallbackExecutor`, sem modelo reserva (embeddings de modelos diferentes não são comparáveis).

### Ingestão de documentos
- [x] Trocar a imagem do PostgreSQL por uma com a extensão pgvector (`pgvector/pgvector:0.8.6-pg17`).
- [x] Tabelas `document` e `document_chunk` (coluna `vector(768)` e índice HNSW por cosseno) criadas pelo Flyway.
- [x] `POST /v1/documents` (multipart): aceitar PDF, TXT e Markdown, até 10 MB.
- [x] Leitura de PDF com o Apache PDFBox.
- [x] Divisão em trechos com o `TextChunker` (1000 caracteres, 200 de sobreposição, cortando em parágrafos e frases).
- [x] Guardar em cada trecho: `client_id`, `document_id`, `chunk_index` e o conteúdo.
- [x] Processamento assíncrono com status (`PROCESSING`, `READY`, `FAILED`) e mensagem de erro.
- [x] `GET /v1/documents`, `GET /v1/documents/{id}` e `DELETE /v1/documents/{id}` (remove também os trechos, em cascata).
- [x] Respeitar os limites do plano gratuito: embeddings em lotes de 50, com pausa entre eles, e no máximo 500 trechos por documento.
- [x] Uso dos embeddings da ingestão atribuído ao cliente dono do documento, mesmo fora da requisição HTTP.

### RAG
- [x] `POST /v1/rag/ask` recebendo a pergunta e, opcionalmente, `topK` e `documentIds`.
- [x] Pipeline próprio: embedding da pergunta → busca vetorial → prompt com os trechos → resposta do modelo.
- [x] **Isolamento por cliente**: `client_id` é coluna obrigatória em toda busca.
- [x] Parâmetros configuráveis: `topK` (padrão 4, máximo 20) e similaridade mínima (`RAG_MIN_SCORE`).
- [x] Retornar as **fontes** usadas (documento, arquivo, trecho, score e início do texto).
- [x] Sem trecho relevante, responder "Não encontrei essa informação nos documentos." sem chamar o modelo de chat.
- [x] Orientar o modelo a tratar os trechos como dados, não instruções (proteção contra prompt injection nos documentos).
- [x] Contabilizar embeddings e chamadas de RAG no `usage_record`.

### Observabilidade
- [x] Dependência `micrometer-registry-prometheus`; `/actuator/prometheus` exposto.
- [x] Métricas próprias com tag `client`: chamadas (por resultado e fallback), tokens, latência (histograma) e requisições recusadas por limite.
- [x] Métricas do Resilience4j (estado dos circuit breakers) e do Spring Boot (HTTP, JVM).
- [x] Prometheus e Grafana no `docker-compose.yml`.
- [x] Datasource e dashboard do Grafana provisionados por arquivos no repositório (`monitoring/`).
- [x] Painéis: chamadas e tokens por cliente, taxa de erro, taxa de cache hit, latência p50/p95/p99, chamadas por resultado, circuit breakers, recusas por limite e uso do modelo reserva.
- [x] Proteger `/actuator/prometheus`: o Actuator roda na porta 8081, publicada só em `localhost`; o Prometheus acessa pela rede interna do Docker.

## Decisões tomadas

- **Embeddings pelo SDK do Google, não pelo Spring AI.** O modelo de embeddings do Spring AI 2.0.1 aceita a opção `task-type` mas não a envia ao Gemini (há um comentário no código dizendo que isso ainda precisa ser verificado). O tipo de tarefa melhora a qualidade da busca, então o provedor usa o SDK diretamente, reaproveitando o cliente configurado na fase 4 (timeout e sem retry escondido). Um teste com WireMock confere que `taskType` e `outputDimensionality` vão na requisição HTTP.
- **Busca vetorial em SQL (pgvector), sem o `PgVectorStore` do Spring AI.** As tabelas são criadas pelo Flyway, o isolamento por cliente é uma coluna obrigatória (não um filtro opcional de metadados), apagar os trechos de um documento é um `ON DELETE CASCADE`, e o Spring AI continua restrito ao pacote `provider`.
- **Busca iterativa do HNSW (`hnsw.iterative_scan`).** Com o filtro por cliente, o índice aproximado pode devolver menos trechos que o pedido; a busca iterativa do pgvector 0.8 continua procurando até completar o resultado.
- **768 dimensões.** O `gemini-embedding-001` gera 3072 por padrão, mas o índice HNSW do pgvector aceita até 2000 dimensões no tipo `vector`. Com 768, o índice funciona e o armazenamento cai para um quarto.
- **Leitura e divisão dos documentos em código próprio** (PDFBox + `TextChunker`), em vez dos leitores e do `TokenTextSplitter` do Spring AI, pelo mesmo motivo de isolamento.
- **Sem trecho relevante, sem chamada ao chat:** economiza a cota e evita respostas inventadas.
- **Só `POST` consome limite:** listar ou apagar documentos não gasta a cota do cliente.
- **Actuator em porta separada (8081):** o endpoint de métricas não fica exposto junto com a API.
- **Dashboard revisado olhando a renderização**, não só o JSON. A primeira versão mostrava "Taxa de erro: 0%" em verde quando não havia tráfego (e com 100% das chamadas falhando); agora o painel fica vazio sem tráfego. Também foram corrigidos painéis vazios que deveriam mostrar zero e legendas cortadas.

## Limitações conhecidas

- **PDFs escaneados** (só imagens) não são suportados: precisariam de OCR. O documento fica `FAILED` com a explicação.
- **Primeiras chamadas de um cliente novo no Grafana:** um contador que já nasce com valor (ex.: 6 chamadas antes da primeira coleta) não aparece no `rate()` do Prometheus, que precisa de uma medição anterior para comparar. Com tráfego contínuo o efeito desaparece.
- **Similaridade mínima** (`RAG_MIN_SCORE`): calibrada na fase 6 com a API real em 0,6 (o padrão era 0,5). Valores altos demais escondem trechos úteis; baixos demais trazem contexto irrelevante.
- As cores das séries por cliente seguem a paleta padrão do Grafana, atribuída pela ordem das séries; os clientes são dinâmicos, então não há cor fixa por cliente.

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
  "answer": "O valor é devolvido em até 7 dias úteis [1].",
  "found": true,
  "sources": [
    { "documentId": 12, "fileName": "politica.pdf", "chunkIndex": 3, "score": 0.87, "excerpt": "..." }
  ],
  "model": "gemini-2.5-flash",
  "provider": "gemini",
  "fallback": false
}
```

## Modelo de dados

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document (
    id              BIGSERIAL PRIMARY KEY,
    client_id       BIGINT       NOT NULL REFERENCES client (id),
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(20)  NOT NULL,   -- PDF | TEXT | MARKDOWN
    size_bytes      BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,   -- PROCESSING | READY | FAILED
    error_message   VARCHAR(500),
    chunk_count     INT,
    embedding_model VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ
);

CREATE TABLE document_chunk (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT      NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    client_id   BIGINT      NOT NULL,
    chunk_index INT         NOT NULL,
    content     TEXT        NOT NULL,
    embedding   vector(768) NOT NULL,
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_document_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);
```

## Critérios de aceite

- [x] Upload de um PDF deixa o documento com status `READY`.
- [x] Pergunta sobre o conteúdo retorna resposta com as fontes (validado com o Gemini real na fase 6).
- [x] Um cliente nunca recebe trechos de documentos de outro cliente (teste automatizado).
- [x] Pergunta fora do conteúdo retorna que não encontrou a informação.
- [x] `docker compose up` sobe o Grafana com o dashboard já configurado.
- [x] Dashboard mostra dados reais após algumas requisições.

## Status

Implementada. 144 testes automatizados passando com `mvn verify` (96 unitários e 48 de integração), cobertura de ~93% das linhas, incluindo RAG com PostgreSQL + pgvector reais, isolamento entre clientes, e WireMock conferindo a requisição de embeddings enviada ao Gemini.
Validado com `docker compose up`: migration do pgvector (extensão 0.8.6), documento processado até `FAILED` com a chave falsa (mensagem correta para erro permanente), Prometheus coletando as métricas e Grafana com datasource e dashboard provisionados, conferido por imagem renderizada.
**Validado com a API real do Gemini na fase 6** (25/09/2026), com os modelos atualizados para `gemini-3.8-flash` e `gemini-3.5-flash-lite` — veja a [fase 6](07-fase-6-extra.md#validação-com-a-api-real-do-gemini).

## Fora desta fase

Segundo provedor de IA (fase 6).

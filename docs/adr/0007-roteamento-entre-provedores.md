# ADR 0007 — Roteamento e fallback entre provedores

**Status:** aceita (fase 6)

## Contexto

Com dois provedores (Gemini e Ollama), é preciso decidir qual atende cada chamada e o que fazer quando um deles está fora do ar.

## Decisão

O `ProviderRouter` escolhe o provedor nesta ordem:

1. o campo `provider` da requisição;
2. o provedor padrão do cliente (`client.default_provider`);
3. o padrão global (`ia-service.providers.default-provider`).

Se o escolhido estiver indisponível (erro transitório ou circuito aberto), o gateway usa o provedor reserva (`ia-service.providers.fallback`), **só quando a requisição não pediu um provedor específico**. A resposta indica `"fallback": true` e não vai para o cache. No streaming, a troca só acontece antes do primeiro trecho.

Embeddings **não** têm fallback entre provedores: os documentos usam sempre o provedor de `ia-service.rag.embedding-provider`.

## Alternativas consideradas

- **Trocar de provedor mesmo com pedido explícito:** mais disponibilidade, mas o cliente que pediu o Ollama (ex.: para não enviar dados à nuvem) receberia uma resposta do Gemini sem ter escolhido isso.
- **Permissão por cliente para escolher provedor:** não foi necessária agora, porque os dois provedores são gratuitos e internos. Fica como evolução.

## Consequências

- O Ollama é opcional: desligado, o bean do provedor nem é criado e o fallback entre provedores fica desativado (com aviso no log).
- `GET /v1/providers` e o health check (`aiProviders`) mostram a situação de cada provedor. O health só fica `DOWN` se nenhum provedor responder, porque um provedor opcional desligado não deve derrubar o serviço.
- Vetores de modelos diferentes não são comparáveis, mesmo com a mesma dimensão. Por isso a busca do RAG filtra pelo modelo que indexou cada documento.

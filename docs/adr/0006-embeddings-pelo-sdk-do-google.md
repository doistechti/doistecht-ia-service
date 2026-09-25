# ADR 0006 — Embeddings pelo SDK do Google

**Status:** aceita (fase 5)

## Contexto

Para RAG, o Gemini gera embeddings melhores quando recebe o tipo de tarefa: `RETRIEVAL_DOCUMENT` ao indexar trechos e `RETRIEVAL_QUERY` ao buscar. O modelo de embeddings do Spring AI 2.0.1 aceita a opção `task-type`, mas **não a envia** ao Gemini; há até um comentário no código dizendo que isso ainda precisava ser verificado.

## Decisão

Gerar embeddings do Gemini com o SDK do Google diretamente, dentro do `GeminiProvider`, reaproveitando o cliente HTTP configurado (timeout e sem retry escondido, ver [ADR 0004](0004-uma-camada-de-retry.md)). O chat continua pelo Spring AI.

No Ollama, o equivalente é o prefixo recomendado pelo `nomic-embed-text` (`search_document:` / `search_query:`), aplicado pelo `OllamaProvider`.

## Alternativas consideradas

- **Usar o Spring AI mesmo assim:** mais simples, mas com busca de pior qualidade, e a falha seria silenciosa.
- **Esperar uma versão corrigida:** o projeto ficaria parado por um problema externo.

## Consequências

- Um teste com WireMock (`GeminiResilienceIT`) confere que `taskType` e `outputDimensionality` vão na requisição HTTP, protegendo contra regressões.
- O provedor Gemini usa duas formas de acesso (Spring AI para chat, SDK para embeddings). A diferença fica escondida atrás de `ModelProvider` e documentada no código.

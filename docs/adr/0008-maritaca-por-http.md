# ADR 0008 — Maritaca chamada direto por HTTP

**Status:** aceita

## Contexto

Os modelos Sabiá, da Maritaca AI, foram treinados para o português do Brasil e entram como terceiro provedor, ao lado do Gemini e do Ollama. A API da Maritaca é compatível com a da OpenAI (`POST /chat/completions` em `https://chat.maritaca.ai/api`), mas não oferece embeddings.

## Decisão

- O `MaritacaProvider` chama a API direto por HTTP: `RestClient` nas chamadas normais e `WebClient` no streaming (Server-Sent Events), com o JSON lido pelo Jackson do projeto. Não entra nenhuma dependência nova.
- O modelo padrão é o `sabiazinho-4`, com modelo reserva opcional (`MARITACA_FALLBACK_MODEL`, ex.: `sabia-4`). Retry, circuit breaker e fallback de modelo vêm do mesmo `ModelFallbackExecutor` dos outros provedores.
- A saída estruturada usa `response_format` com `json_schema`, como na OpenAI; o gateway continua validando a resposta contra o schema.
- O provedor é opcional (`MARITACA_ENABLED`) e entra só por escolha: no campo `provider` da requisição ou no provedor padrão do cliente. O padrão global continua o Gemini.
- Pedir embeddings à Maritaca devolve `400` (`UnsupportedCapabilityException`), porque repetir a chamada nunca daria certo.

## Alternativas consideradas

- **Módulo OpenAI do Spring AI apontando para a Maritaca:** menos código, mas a autoconfiguração do starter cria beans da OpenAI ao lado dos outros provedores, e não há garantia de que a Maritaca aceite tudo o que o cliente da OpenAI envia.
- **SDK oficial da OpenAI:** dependência grande para usar um único endpoint.

## Consequências

- Mudanças na API da Maritaca afetam só `provider/maritaca`.
- Os documentos do RAG continuam indexados pelo `RAG_EMBEDDING_PROVIDER`; a Maritaca pode responder às perguntas usando esses trechos.
- A Maritaca é paga. O custo estimado só aparece depois que o preço do modelo for informado em `ia-service.pricing`.

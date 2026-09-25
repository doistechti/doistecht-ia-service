# ADR 0005 — RAG próprio com pgvector em SQL

**Status:** aceita (fase 5, ajustada na fase 6)

## Contexto

O serviço precisa responder perguntas com base nos documentos de cada cliente. O Spring AI oferece o `PgVectorStore`, leitores de documentos, o `TokenTextSplitter` e o `QuestionAnswerAdvisor`.

## Decisão

Implementar o pipeline no projeto:

- **Leitura:** Apache PDFBox para PDF; TXT e MD como texto UTF-8.
- **Divisão:** `TextChunker` próprio (1000 caracteres, 200 de sobreposição, cortando em parágrafos e frases).
- **Armazenamento e busca:** tabelas `document` e `document_chunk` criadas pelo Flyway, coluna `vector(768)`, índice HNSW por cosseno e consultas em SQL puro.
- **Isolamento:** `client_id` é coluna obrigatória em toda busca.
- **Compatibilidade de vetores:** a busca só compara trechos indexados pelo mesmo modelo de embedding da pergunta.

## Alternativas consideradas

- **`PgVectorStore` do Spring AI:** menos código, mas cria a própria tabela, guarda o cliente em metadados JSON (filtro opcional, fácil de esquecer) e acopla o RAG ao Spring AI.
- **Banco vetorial dedicado** (Qdrant, Weaviate): mais um serviço para operar, sem ganho relevante no volume esperado.

## Consequências

- Não há como uma busca esquecer o filtro e devolver trechos de outro cliente.
- Apagar um documento apaga os trechos em cascata.
- **768 dimensões:** o índice HNSW do pgvector aceita até 2000 no tipo `vector`, e o `gemini-embedding-001` gera 3072 por padrão.
- Com o filtro por cliente, o HNSW pode devolver menos trechos que o pedido; a busca iterativa do pgvector 0.8 (`hnsw.iterative_scan`) completa o resultado.
- A similaridade mínima (`RAG_MIN_SCORE`) foi calibrada com a API real em 0,6: perguntas relevantes ficaram acima de 0,64 e perguntas sem relação com o documento, perto de 0,55. É uma amostra pequena e deve ser revista com documentos reais.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document (
    id             BIGSERIAL PRIMARY KEY,
    client_id      BIGINT        NOT NULL REFERENCES client (id),
    file_name      VARCHAR(255)  NOT NULL,
    content_type   VARCHAR(20)   NOT NULL,
    size_bytes     BIGINT        NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    error_message  VARCHAR(500),
    chunk_count    INT,
    embedding_model VARCHAR(100),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    processed_at   TIMESTAMPTZ,
    CONSTRAINT ck_document_status CHECK (status IN ('PROCESSING', 'READY', 'FAILED'))
);

CREATE INDEX idx_document_client ON document (client_id, created_at DESC);

-- Trechos dos documentos com seus embeddings. A dimensão (768) precisa ser igual à
-- configurada em ia-service.gemini.embedding-dimensions.
CREATE TABLE document_chunk (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT       NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    -- Repetido aqui para filtrar a busca vetorial por cliente sem precisar de join
    client_id   BIGINT       NOT NULL,
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    embedding   vector(768)  NOT NULL,
    CONSTRAINT uk_document_chunk_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_document_chunk_client ON document_chunk (client_id);

-- Índice aproximado (HNSW) para busca por similaridade de cosseno
CREATE INDEX idx_document_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);

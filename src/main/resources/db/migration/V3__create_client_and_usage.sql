CREATE TABLE client (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(100) NOT NULL,
    api_key_hash          VARCHAR(64)  NOT NULL,
    api_key_prefix        VARCHAR(16)  NOT NULL,
    rate_limit_per_minute INT          NOT NULL,
    daily_quota           INT          NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_client_name UNIQUE (name),
    CONSTRAINT uk_client_api_key_hash UNIQUE (api_key_hash),
    CONSTRAINT ck_client_limits CHECK (rate_limit_per_minute > 0 AND daily_quota > 0)
);

-- Uma linha por chamada ao provedor (ou resposta servida do cache)
CREATE TABLE usage_record (
    id             BIGSERIAL PRIMARY KEY,
    client_id      BIGINT        NOT NULL REFERENCES client (id),
    endpoint       VARCHAR(200)  NOT NULL,
    operation      VARCHAR(20)   NOT NULL,
    provider       VARCHAR(30)   NOT NULL,
    model          VARCHAR(100),
    prompt_tokens  INT,
    output_tokens  INT,
    latency_ms     INT           NOT NULL,
    estimated_cost NUMERIC(12, 6),
    cache_hit      BOOLEAN       NOT NULL DEFAULT FALSE,
    success        BOOLEAN       NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_usage_client_date ON usage_record (client_id, created_at);

-- Templates passam a poder pertencer a um cliente (client_id nulo = template global)
ALTER TABLE prompt_template ADD COLUMN client_id BIGINT REFERENCES client (id);

ALTER TABLE prompt_template DROP CONSTRAINT uk_prompt_template_name_version;

ALTER TABLE prompt_template
    ADD CONSTRAINT uk_prompt_template_scope_name_version UNIQUE NULLS NOT DISTINCT (client_id, name, version);

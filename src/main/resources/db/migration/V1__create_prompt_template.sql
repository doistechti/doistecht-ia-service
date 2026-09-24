CREATE TABLE prompt_template (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(100) NOT NULL,
    version              INT          NOT NULL,
    system_prompt        TEXT,
    user_prompt_template TEXT         NOT NULL,
    output_schema        JSONB,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_prompt_template_name_version UNIQUE (name, version)
);

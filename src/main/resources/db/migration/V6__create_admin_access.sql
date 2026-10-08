CREATE TABLE admin_user (
    id                   BIGSERIAL PRIMARY KEY,
    email                VARCHAR(254) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    role                 VARCHAR(30)  NOT NULL DEFAULT 'ADMIN',
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_admin_user_email UNIQUE (email),
    CONSTRAINT ck_admin_user_role CHECK (role IN ('ADMIN'))
);

CREATE TABLE admin_session (
    id            BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT       NOT NULL REFERENCES admin_user (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64)  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_admin_session_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_admin_session_expiration ON admin_session (expires_at);

-- Administrador inicial. A senha nunca e persistida em texto puro e deve ser trocada no primeiro acesso.
INSERT INTO admin_user (email, password_hash, role, must_change_password)
VALUES (
    'doistechti@gmail.com',
    'pbkdf2-sha256$210000$78LoEpTNejdzoono7GrTzg==$CMbIsOS2+5rJXLOsFNiGLkOmh9AaUyjku/BERDZRrz0=',
    'ADMIN',
    TRUE
);

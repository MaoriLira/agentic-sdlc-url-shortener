CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE core.api_clients (
    id            BIGSERIAL PRIMARY KEY,
    api_key_hash  VARCHAR(128) NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE','SUSPENDED'))
);

CREATE TABLE core.url_mappings (
    id               BIGSERIAL PRIMARY KEY,
    short_code       VARCHAR(20) NOT NULL UNIQUE,
    long_url         TEXT NOT NULL,
    owner_client_id  BIGINT REFERENCES core.api_clients(id),
    is_custom_alias  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('ACTIVE','EXPIRED','DELETED'))
);

CREATE INDEX idx_url_mappings_short_code_active
    ON core.url_mappings (short_code) WHERE status = 'ACTIVE';
CREATE INDEX idx_url_mappings_owner
    ON core.url_mappings (owner_client_id);
CREATE INDEX idx_url_mappings_expires_at
    ON core.url_mappings (expires_at) WHERE expires_at IS NOT NULL;

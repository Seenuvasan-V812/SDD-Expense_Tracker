-- T017: refresh_tokens — family_id NOT NULL (SEC-2 rotation), SHA-256 hash storage
-- AC: family_id UUID NOT NULL, token_hash UNIQUE, idx family/expires/user

CREATE TABLE refresh_tokens (
    id           UUID         NOT NULL,
    user_id      UUID         NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    family_id    UUID         NOT NULL,
    revoked_at   TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_refresh_tokens            PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash       UNIQUE  (token_hash),
    CONSTRAINT fk_refresh_tokens_user       FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_family_id  ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens (user_id);

-- T019: password_reset_tokens — token_hash UNIQUE, consumed_at nullable, FK→users CASCADE
-- AC: token_hash UNIQUE, consumed_at?, FK→users CASCADE

CREATE TABLE password_reset_tokens (
    id           UUID         NOT NULL,
    user_id      UUID         NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    consumed_at  TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_password_reset_tokens       PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_hash  UNIQUE  (token_hash),
    CONSTRAINT fk_password_reset_tokens_user  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);

-- T018: email_verifications — token_hash UNIQUE, consumed_at nullable, FK→users CASCADE
-- AC: token_hash UNIQUE, consumed_at?, FK→users CASCADE

CREATE TABLE email_verifications (
    id           UUID         NOT NULL,
    user_id      UUID         NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    consumed_at  TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_email_verifications       PRIMARY KEY (id),
    CONSTRAINT uq_email_verifications_hash  UNIQUE  (token_hash),
    CONSTRAINT fk_email_verifications_user  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_email_verifications_user ON email_verifications (user_id);

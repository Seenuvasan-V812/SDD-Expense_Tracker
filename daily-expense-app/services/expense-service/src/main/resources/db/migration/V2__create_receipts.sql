-- T050: receipts — uq_receipts_expense_id, mime_type CHECK, size_bytes CHECK<=5242880
-- FK on expense_id CASCADE (intra-service; user_id denormalized for ownership queries)

CREATE TABLE receipts (
    id                UUID          NOT NULL,
    expense_id        UUID          NOT NULL,
    user_id           UUID          NOT NULL,
    storage_key       VARCHAR(500)  NOT NULL,
    mime_type         VARCHAR(30)   NOT NULL,
    file_size_bytes   BIGINT        NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_receipts              PRIMARY KEY (id),
    CONSTRAINT uq_receipts_expense_id   UNIQUE (expense_id),
    CONSTRAINT fk_receipts_expense      FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
    CONSTRAINT ck_receipts_mime_type    CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_receipts_size_max     CHECK (file_size_bytes <= 5242880)
);

CREATE INDEX idx_receipts_user_id ON receipts (user_id);

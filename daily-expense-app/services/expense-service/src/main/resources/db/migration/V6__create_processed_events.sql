-- T065 idempotency dedup for CSV import; Phase 2 consumer dedup

CREATE TABLE processed_imports (
    idempotency_key VARCHAR(255) NOT NULL,
    user_id         UUID         NOT NULL,
    processed_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    result_json     TEXT         NOT NULL,

    CONSTRAINT pk_processed_imports PRIMARY KEY (idempotency_key, user_id)
);

-- Phase 2 event-consumer dedup
CREATE TABLE processed_events (
    event_id     UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);

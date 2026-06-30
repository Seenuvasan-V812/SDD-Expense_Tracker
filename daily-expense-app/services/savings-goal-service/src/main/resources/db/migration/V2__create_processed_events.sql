-- T097 — Idempotent-consume guard for savings-goal-service event consumers.
-- insert-before-process: INSERT fails on dup PK → consumer skips duplicate delivery.

CREATE TABLE processed_events (
    event_id     UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);

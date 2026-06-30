-- Transactional outbox for category-service (CQ-8 / T094)
-- Each service has its OWN outbox table (AL-1 — no shared table).
-- OutboxPublisher writes here in the SAME @Transactional as the business row.

CREATE TABLE outbox (
    id             UUID          NOT NULL,
    event_id       UUID          NOT NULL,
    aggregate_type VARCHAR(100)  NOT NULL,
    aggregate_id   UUID          NOT NULL,
    event_type     VARCHAR(200)  NOT NULL,
    payload        TEXT          NOT NULL,
    published      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,

    CONSTRAINT pk_outbox          PRIMARY KEY (id),
    CONSTRAINT uq_outbox_event_id UNIQUE  (event_id)
);

-- Relay polls this index: published=false only (partial index keeps it small).
CREATE INDEX idx_outbox_unpublished ON outbox (published, created_at) WHERE published = FALSE;

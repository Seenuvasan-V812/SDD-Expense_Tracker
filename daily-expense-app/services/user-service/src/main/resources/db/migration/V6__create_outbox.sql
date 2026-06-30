-- Transactional outbox for user-service (CQ-8 / T014 / T025)
-- OutboxPublisher writes here in the SAME @Transactional as business row; relay picks up unpublished rows.

CREATE TABLE outbox (
    id              UUID          NOT NULL,
    event_id        UUID          NOT NULL,
    aggregate_type  VARCHAR(100)  NOT NULL,
    aggregate_id    UUID          NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    payload         TEXT          NOT NULL,
    published       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,

    CONSTRAINT pk_outbox          PRIMARY KEY (id),
    CONSTRAINT uq_outbox_event_id UNIQUE  (event_id)
);

CREATE INDEX idx_outbox_unpublished ON outbox (published, created_at) WHERE published = FALSE;

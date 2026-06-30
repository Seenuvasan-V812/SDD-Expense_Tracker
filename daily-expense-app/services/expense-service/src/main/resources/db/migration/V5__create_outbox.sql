-- Transactional outbox for expense-service (CQ-8 / T057)
-- Written in the SAME @Transactional as the state change; relay polls and publishes to Kafka.

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
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX idx_outbox_unpublished ON outbox (published, created_at) WHERE published = FALSE;

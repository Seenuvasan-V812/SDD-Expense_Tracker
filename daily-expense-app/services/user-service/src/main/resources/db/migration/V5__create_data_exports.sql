-- T020: data_exports — status CHECK(REQUESTED,READY,FAILED), download_ref?, FK→users CASCADE
-- AC: status CHECK, download_ref nullable, FK cascade

CREATE TABLE data_exports (
    id             UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    status         VARCHAR(25)  NOT NULL DEFAULT 'REQUESTED',
    download_ref   VARCHAR(500),
    requested_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_data_exports        PRIMARY KEY (id),
    CONSTRAINT ck_data_exports_status CHECK  (status IN ('REQUESTED', 'READY', 'FAILED')),
    CONSTRAINT fk_data_exports_user   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TRIGGER trg_data_exports_updated_at
    BEFORE UPDATE ON data_exports
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_data_exports_user ON data_exports (user_id);

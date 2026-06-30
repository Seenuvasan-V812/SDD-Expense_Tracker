-- T016: users table + set_updated_at trigger
-- Constraints: status CHECK, password_hash VARCHAR(72), audit cols, uq_users_email (AC: T016)

CREATE TABLE users (
    id                     UUID          NOT NULL,
    full_name              VARCHAR(100)  NOT NULL,
    email                  VARCHAR(255)  NOT NULL,
    password_hash          VARCHAR(72)   NOT NULL,
    status                 VARCHAR(25)   NOT NULL DEFAULT 'INACTIVE_UNVERIFIED',
    preferred_currency     VARCHAR(3)    NOT NULL DEFAULT 'INR',
    timezone               VARCHAR(50)   NOT NULL DEFAULT 'Asia/Kolkata',
    locale                 VARCHAR(10)   NOT NULL DEFAULT 'en-IN',
    weekly_digest_enabled  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_users            PRIMARY KEY (id),
    CONSTRAINT uq_users_email      UNIQUE  (email),
    CONSTRAINT ck_users_status     CHECK   (status IN ('INACTIVE_UNVERIFIED', 'ACTIVE', 'DELETED')),
    CONSTRAINT ck_users_currency   CHECK   (preferred_currency = 'INR')
);

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_users_status          ON users (status);
CREATE INDEX idx_users_weekly_digest   ON users (weekly_digest_enabled) WHERE weekly_digest_enabled = TRUE;

-- T069: savings_goals table for savings_goal_db (Phase 3a)
-- status: ACTIVE|PAUSED|COMPLETED|ABANDONED  (SG-INV-6 auto-complete when total>=target while ACTIVE)
-- total_contributed is a derived cache kept consistent with backing Expenses (SG-INV-3)
-- Note: V1=outbox (T094), V2=processed_events (T097) — business tables start at V3

CREATE TABLE savings_goals (
    id                UUID            NOT NULL,
    user_id           UUID            NOT NULL,
    name              VARCHAR(255)    NOT NULL,
    target_amount     NUMERIC(19, 4)  NOT NULL,
    currency          VARCHAR(3)      NOT NULL DEFAULT 'INR',
    target_date       DATE,
    description       TEXT,
    status            VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    total_contributed NUMERIC(19, 4)  NOT NULL DEFAULT 0.0000,
    icon              VARCHAR(100),
    color             VARCHAR(50),
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_savings_goals              PRIMARY KEY (id),
    CONSTRAINT ck_savings_goals_target       CHECK (target_amount > 0),
    CONSTRAINT ck_savings_goals_total        CHECK (total_contributed >= 0),
    CONSTRAINT ck_savings_goals_status       CHECK (status IN ('ACTIVE','PAUSED','COMPLETED','ABANDONED')),
    CONSTRAINT ck_savings_goals_currency     CHECK (currency = 'INR')
);

CREATE INDEX idx_savings_goals_user_id     ON savings_goals (user_id);
CREATE INDEX idx_savings_goals_user_status ON savings_goals (user_id, status);

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_savings_goals_updated_at
    BEFORE UPDATE ON savings_goals
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- T084: budget_period_ledgers for budget_db (BUD-INV-5/8)
-- fired_eighty_percent / fired_exceeded: once-per-period idempotency flags (BUD-INV-5)
-- uq_budget_period_ledgers_budget_window: prevents duplicate ledger on re-run (BUD-INV-8)
-- spent is derived from SpendingFeedPort events only — never from expense_db (BUD-INV-4)

CREATE TABLE budget_period_ledgers (
    id                    UUID            NOT NULL,
    budget_id             UUID            NOT NULL,
    user_id               UUID            NOT NULL,
    period_start          DATE            NOT NULL,
    period_end            DATE            NOT NULL,
    carried_in            NUMERIC(19, 4)  NOT NULL DEFAULT 0.0000,
    spent                 NUMERIC(19, 4)  NOT NULL DEFAULT 0.0000,
    currency              VARCHAR(3)      NOT NULL DEFAULT 'INR',
    fired_eighty_percent  BOOLEAN         NOT NULL DEFAULT FALSE,
    fired_exceeded        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_budget_period_ledgers                       PRIMARY KEY (id),
    CONSTRAINT fk_budget_period_ledgers_budget                FOREIGN KEY (budget_id)
        REFERENCES budgets(id) ON DELETE CASCADE,
    CONSTRAINT uq_budget_period_ledgers_budget_window         UNIQUE (budget_id, period_start),
    CONSTRAINT ck_budget_period_ledgers_period                CHECK (period_end >= period_start),
    CONSTRAINT ck_budget_period_ledgers_carried_in            CHECK (carried_in >= 0),
    CONSTRAINT ck_budget_period_ledgers_spent                 CHECK (spent >= 0),
    CONSTRAINT ck_budget_period_ledgers_currency              CHECK (currency = 'INR')
);

CREATE INDEX idx_budget_period_ledgers_budget_id   ON budget_period_ledgers (budget_id);
CREATE INDEX idx_budget_period_ledgers_user_period  ON budget_period_ledgers (user_id, period_start DESC);

CREATE TRIGGER trg_budget_period_ledgers_updated_at
    BEFORE UPDATE ON budget_period_ledgers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

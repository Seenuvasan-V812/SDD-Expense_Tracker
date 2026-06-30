-- T083: budgets table for budget_db (Phase 3b)
-- scope: OVERALL | CATEGORY (categoryId required iff CATEGORY — ck_budgets_scope_category)
-- V3 because V1=outbox (T094), V2=processed_events (T097)
-- BUD-INV-1: budget_limit > 0
-- ck_budgets_scope_category: if scope=CATEGORY then category_id NOT NULL, else NULL

CREATE TABLE budgets (
    id               UUID            NOT NULL,
    user_id          UUID            NOT NULL,
    scope            VARCHAR(10)     NOT NULL,
    category_id      UUID,
    budget_limit     NUMERIC(19, 4)  NOT NULL,
    currency         VARCHAR(3)      NOT NULL DEFAULT 'INR',
    period_type      VARCHAR(10)     NOT NULL,
    active           BOOLEAN         NOT NULL DEFAULT TRUE,
    rollover_enabled BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_budgets                    PRIMARY KEY (id),
    CONSTRAINT ck_budgets_scope              CHECK (scope IN ('OVERALL', 'CATEGORY')),
    CONSTRAINT ck_budgets_limit              CHECK (budget_limit > 0),
    CONSTRAINT ck_budgets_currency           CHECK (currency = 'INR'),
    CONSTRAINT ck_budgets_period_type        CHECK (period_type IN ('WEEKLY', 'MONTHLY')),
    CONSTRAINT ck_budgets_scope_category     CHECK (
        (scope = 'CATEGORY' AND category_id IS NOT NULL) OR
        (scope = 'OVERALL'  AND category_id IS NULL)
    )
);

CREATE INDEX idx_budgets_user_id   ON budgets (user_id);
CREATE INDEX idx_budgets_active    ON budgets (user_id) WHERE active = TRUE;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_budgets_updated_at
    BEFORE UPDATE ON budgets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- T049: expenses — amount CHECK>0, payment_method CHECK(6), no FK on category_id/savings_goal_id (AL-1)
-- Composite index: idx_expenses_user_date (user_id, expense_date DESC)

CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE expenses (
    id                   UUID            NOT NULL,
    user_id              UUID            NOT NULL,
    amount               NUMERIC(19, 4)  NOT NULL,
    currency             VARCHAR(3)      NOT NULL DEFAULT 'INR',
    expense_date         DATE            NOT NULL,
    category_id          UUID            NOT NULL,           -- xref, no FK (cross-context AL-1)
    payment_method       VARCHAR(20)     NOT NULL,
    description          VARCHAR(255),
    merchant             VARCHAR(255),
    notes                TEXT,
    savings_goal_id      UUID,                               -- xref, no FK (cross-context AL-1)
    recurring_expense_id UUID,                               -- FK added in V4 after table exists
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_expenses             PRIMARY KEY (id),
    CONSTRAINT ck_expenses_amount      CHECK (amount > 0),
    CONSTRAINT ck_expenses_payment_method CHECK (
        payment_method IN ('UPI', 'CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'NET_BANKING', 'OTHER')
    )
);

CREATE TRIGGER trg_expenses_updated_at
    BEFORE UPDATE ON expenses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_expenses_user_id              ON expenses (user_id);
CREATE INDEX idx_expenses_category_id          ON expenses (category_id);
CREATE INDEX idx_expenses_payment_method       ON expenses (payment_method);
CREATE INDEX idx_expenses_savings_goal_id      ON expenses (savings_goal_id);
CREATE INDEX idx_expenses_expense_date         ON expenses (expense_date);
CREATE INDEX idx_expenses_user_date            ON expenses (user_id, expense_date DESC);

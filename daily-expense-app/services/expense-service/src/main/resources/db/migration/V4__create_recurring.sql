-- T052: recurring_expenses + recurring_expense_tags + FK from expenses.recurring_expense_id

CREATE TABLE recurring_expenses (
    id                   UUID           NOT NULL,
    user_id              UUID           NOT NULL,
    amount               NUMERIC(19, 4) NOT NULL,
    currency             VARCHAR(3)     NOT NULL DEFAULT 'INR',
    category_id          UUID           NOT NULL,
    payment_method       VARCHAR(20)    NOT NULL,
    description          VARCHAR(255),
    merchant             VARCHAR(255),
    notes                TEXT,
    frequency            VARCHAR(10)    NOT NULL,
    anchor_date          DATE           NOT NULL,
    end_date             DATE,
    max_occurrences      INT,
    generated_count      INT            NOT NULL DEFAULT 0,
    next_run_date        DATE,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT pk_recurring_expenses            PRIMARY KEY (id),
    CONSTRAINT ck_recurring_amount              CHECK (amount > 0),
    CONSTRAINT ck_recurring_frequency           CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY')),
    CONSTRAINT ck_recurring_max_occurrences     CHECK (max_occurrences IS NULL OR max_occurrences > 0),
    CONSTRAINT ck_recurring_payment_method      CHECK (
        payment_method IN ('UPI', 'CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'NET_BANKING', 'OTHER')
    )
);

CREATE TRIGGER trg_recurring_expenses_updated_at
    BEFORE UPDATE ON recurring_expenses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_recurring_expenses_user_id       ON recurring_expenses (user_id);
CREATE INDEX idx_recurring_expenses_next_run_date ON recurring_expenses (next_run_date);

CREATE TABLE recurring_expense_tags (
    recurring_expense_id UUID NOT NULL,
    tag_id               UUID NOT NULL,

    CONSTRAINT pk_recurring_expense_tags           PRIMARY KEY (recurring_expense_id, tag_id),
    CONSTRAINT fk_recurring_expense_tags_recurring  FOREIGN KEY (recurring_expense_id)
        REFERENCES recurring_expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_expense_tags_tag        FOREIGN KEY (tag_id)
        REFERENCES tags(id) ON DELETE CASCADE
);

-- Add FK on expenses.recurring_expense_id now that recurring_expenses exists (intra-service)
ALTER TABLE expenses
    ADD CONSTRAINT fk_expenses_recurring_expense
        FOREIGN KEY (recurring_expense_id) REFERENCES recurring_expenses(id) ON DELETE SET NULL;

CREATE INDEX idx_expenses_recurring_expense_id ON expenses (recurring_expense_id);

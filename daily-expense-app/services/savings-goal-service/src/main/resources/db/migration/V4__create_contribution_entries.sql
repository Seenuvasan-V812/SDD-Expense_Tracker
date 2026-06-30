-- T070: contribution_entries for savings_goal_db (SG-INV-4)
-- expense_id is a cross-context reference — NO foreign key to expense_db (AL-1)
-- uq_contribution_entries_goal_expense: one entry per backing Expense per goal
-- source: GOAL_SCREEN (primary/direct) | LINKED_EXPENSE (secondary/event-driven)

CREATE TABLE contribution_entries (
    id               UUID            NOT NULL,
    savings_goal_id  UUID            NOT NULL,
    user_id          UUID            NOT NULL,
    expense_id       UUID            NOT NULL,
    amount           NUMERIC(19, 4)  NOT NULL,
    currency         VARCHAR(3)      NOT NULL DEFAULT 'INR',
    entry_date       DATE            NOT NULL,
    source           VARCHAR(20)     NOT NULL,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_contribution_entries                PRIMARY KEY (id),
    CONSTRAINT fk_contribution_entries_goal           FOREIGN KEY (savings_goal_id)
        REFERENCES savings_goals(id) ON DELETE CASCADE,
    CONSTRAINT uq_contribution_entries_goal_expense   UNIQUE (savings_goal_id, expense_id),
    CONSTRAINT ck_contribution_entries_amount         CHECK (amount > 0),
    CONSTRAINT ck_contribution_entries_currency       CHECK (currency = 'INR'),
    CONSTRAINT ck_contribution_entries_source         CHECK (source IN ('GOAL_SCREEN','LINKED_EXPENSE'))
);

CREATE INDEX idx_contribution_entries_goal_id    ON contribution_entries (savings_goal_id);
CREATE INDEX idx_contribution_entries_user_id    ON contribution_entries (user_id);
CREATE INDEX idx_contribution_entries_expense_id ON contribution_entries (expense_id);

CREATE TRIGGER trg_contribution_entries_updated_at
    BEFORE UPDATE ON contribution_entries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

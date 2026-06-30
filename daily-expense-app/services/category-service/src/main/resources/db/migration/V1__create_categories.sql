-- T040: categories — type/origin/system_role CHECK, partial Savings index, owner uniqueness
-- AC: ck_categories_default_no_owner, uq_categories_owner_name, partial idx_categories_system_role WHERE system_role='SAVINGS'

CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE categories (
    id          UUID         NOT NULL,
    user_id     UUID,                          -- NULL = DEFAULT/system; set = Custom
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(25)  NOT NULL,
    origin      VARCHAR(25)  NOT NULL DEFAULT 'CUSTOM',
    system_role VARCHAR(25)  NOT NULL DEFAULT 'NONE',
    icon        VARCHAR(100),
    color       VARCHAR(20),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_categories PRIMARY KEY (id),

    CONSTRAINT ck_categories_type        CHECK (type IN ('EXPENSE', 'INCOME', 'BOTH')),
    CONSTRAINT ck_categories_origin      CHECK (origin IN ('DEFAULT', 'CUSTOM')),
    CONSTRAINT ck_categories_system_role CHECK (system_role IN ('NONE', 'SAVINGS')),

    -- DEFAULT categories must have NULL owner; CUSTOM categories must have an owner
    CONSTRAINT ck_categories_default_no_owner CHECK (
        (origin = 'DEFAULT' AND user_id IS NULL) OR
        (origin = 'CUSTOM'  AND user_id IS NOT NULL)
    ),

    -- Custom category name unique per owner (NULL != NULL in standard SQL, so this covers CUSTOM only)
    CONSTRAINT uq_categories_owner_name UNIQUE (user_id, name)
);

-- Separate unique index for DEFAULT category names (NULLs not equal in UNIQUE constraint)
CREATE UNIQUE INDEX uq_categories_default_name ON categories (name) WHERE user_id IS NULL;

CREATE TRIGGER trg_categories_updated_at
    BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_categories_user_id ON categories (user_id);
CREATE INDEX idx_categories_type    ON categories (type);

-- Partial index for fast Savings Category lookup (AC: T040)
CREATE INDEX idx_categories_system_role ON categories (system_role) WHERE system_role = 'SAVINGS';

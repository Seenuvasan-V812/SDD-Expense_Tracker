-- T051: tags (uq_tags_owner_name) + expense_tags join table (PK=(expense_id,tag_id), both FK CASCADE)

CREATE TABLE tags (
    id         UUID          NOT NULL,
    user_id    UUID          NOT NULL,
    name       VARCHAR(100)  NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_tags           PRIMARY KEY (id),
    CONSTRAINT uq_tags_owner_name UNIQUE (user_id, name)
);

CREATE INDEX idx_tags_user_id ON tags (user_id);

CREATE TABLE expense_tags (
    expense_id UUID NOT NULL,
    tag_id     UUID NOT NULL,

    CONSTRAINT pk_expense_tags        PRIMARY KEY (expense_id, tag_id),
    CONSTRAINT fk_expense_tags_expense FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_expense_tags_tag     FOREIGN KEY (tag_id)     REFERENCES tags(id)     ON DELETE CASCADE
);

CREATE INDEX idx_expense_tags_tag_id ON expense_tags (tag_id);

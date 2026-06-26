# Data Contract Specification — Daily Expense Application

| Field | Value |
|-------|-------|
| **Document** | 09 — Data Contract Specification (PostgreSQL, per microservice) |
| **Feature** | Daily Expense Application |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Status** | Draft |
| **Created** | 2026-06-25 |
| **Author Role** | Principal Database Architect |
| **Source Inputs** | `06-aggregate-specifications.md`, `05-domain-model.md`, `.specify/memory/constitution.md` (v1.1.1) |
| **Governing Authority** | [Daily Expense Application — Engineering Constitution](../../.specify/memory/constitution.md) |
| **Vocabulary Authority** | [Ubiquitous Language Glossary](./02-glossary.md) |

> **Purpose.** Define the PostgreSQL physical data contract for each bounded-context microservice.
> Per **Service Isolation** (AL-1): **each service owns its own database/schema; there are NO
> cross-schema joins and NO foreign keys across services.** Cross-context references are stored as
> bare **UUID id columns** (e.g. `user_id`, `category_id`, `savings_goal_id`) that are validated at
> the application layer via Anti-Corruption Ports (AL-2) — never enforced by a database FK to another
> service's table.

---

## 1. Global Data Standards (binding for every table)

### 1.1 Hard rules

| # | Rule | Source |
|---|------|--------|
| DB-1 | **One schema/database per service.** No table is read or joined across service boundaries. | AL-1 |
| DB-2 | **No cross-service foreign keys.** Intra-service FKs are allowed and encouraged; cross-context refs are plain `UUID` columns validated via port (AL-2). | AL-1/AL-2 |
| DB-3 | **Every table has `created_at` and `updated_at`** (`TIMESTAMPTZ NOT NULL`), auto-populated. | CQ-9 |
| DB-4 | **Every filtered/joined column is indexed.** All `user_id` and intra-service FK columns carry an index. | CQ-10 |
| DB-5 | **Money is `NUMERIC(19,4)`** (never `float`/`double`); currency stored alongside, default `'INR'`. | Glossary, CQ-3 |
| DB-6 | **Ownership column `user_id UUID NOT NULL`** on every user-owned table; the application enforces 403-never-404 (SEC-3) — the column is the filter key, not a cross-service FK. | P4, SEC-3 |
| DB-7 | **Enums stored as `VARCHAR` + `CHECK` constraint** (portable, explicit) using `UPPER_SNAKE_CASE` values matching the domain enums. | CQ-3 |
| DB-8 | **PKs are `UUID`** (application-generated), `PRIMARY KEY`. | §6 naming |
| DB-9 | Bulk reads (reports/exports) stream / paginate; no query loads all rows into memory. | CQ-10 |

### 1.2 Naming conventions (Constitution §6)

| Artefact | Convention | Example |
|----------|------------|---------|
| Table | `snake_case`, **plural** | `expenses`, `savings_goals` |
| Column | `snake_case` | `payment_method`, `created_at` |
| Index | `idx_<table>_<column(s)>` | `idx_expenses_category_id` |
| Unique constraint | `uq_<table>_<column(s)>` | `uq_users_email` |
| Check constraint | `ck_<table>_<rule>` | `ck_expenses_amount_positive` |
| Foreign key (intra-service only) | `fk_<table>_<ref>` | `fk_receipts_expense_id` |

### 1.3 Shared audit + money definition (applied to all tables)

```sql
-- Audit columns present on EVERY table (DB-3)
created_at  TIMESTAMPTZ  NOT NULL  DEFAULT now(),
updated_at  TIMESTAMPTZ  NOT NULL  DEFAULT now()
-- updated_at maintained by a BEFORE UPDATE trigger per table (set_updated_at()).

-- Money columns (DB-5): amount + currency pair
amount       NUMERIC(19,4) NOT NULL,
currency     VARCHAR(3)    NOT NULL DEFAULT 'INR'  CHECK (currency = 'INR')
```

> **`updated_at` trigger.** Each service defines one reusable function `set_updated_at()` and a
> `BEFORE UPDATE` trigger per table; not repeated per-table below for brevity (CQ-9).

---

## 2. Database `identity_db`  *(service: `user-service` — Identity & Access)*

Tables: `users`, `email_verifications`, `refresh_tokens`, `password_reset_tokens`, `data_exports`.

### 2.1 `users`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `full_name` | `VARCHAR(150)` | `NOT NULL` |
| `email` | `VARCHAR(255)` | `NOT NULL`, `UNIQUE` (`uq_users_email`) |
| `password_hash` | `VARCHAR(72)` | `NOT NULL` (BCrypt; plain text never stored — SEC-1) |
| `status` | `VARCHAR(24)` | `NOT NULL`, `CHECK (status IN ('INACTIVE_UNVERIFIED','ACTIVE','DELETED'))` |
| `preferred_currency` | `VARCHAR(3)` | `NOT NULL DEFAULT 'INR'` |
| `timezone` | `VARCHAR(64)` | `NOT NULL DEFAULT 'Asia/Kolkata'` |
| `locale` | `VARCHAR(16)` | `NOT NULL DEFAULT 'en-IN'` |
| `weekly_digest_enabled` | `BOOLEAN` | `NOT NULL DEFAULT false` (REQ-NOTIF-001 / N-01) |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Indexes:** `uq_users_email` (unique); `idx_users_status` (filter active users for metrics); `idx_users_weekly_digest` on `weekly_digest_enabled` (scheduler fan-out for Weekly Digest).

### 2.2 `email_verifications`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `user_id` | `UUID` | `NOT NULL`, `fk_email_verifications_user_id` → `users(id)` |
| `token_hash` | `VARCHAR(255)` | `NOT NULL`, `UNIQUE` |
| `expires_at` | `TIMESTAMPTZ` | `NOT NULL` |
| `consumed_at` | `TIMESTAMPTZ` | `NULL` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Indexes:** `idx_email_verifications_user_id`; `uq_email_verifications_token_hash`.

### 2.3 `refresh_tokens`  *(rotating — SEC-2)*

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `user_id` | `UUID` | `NOT NULL`, `fk_refresh_tokens_user_id` → `users(id)` |
| `token_hash` | `VARCHAR(255)` | `NOT NULL`, `UNIQUE` |
| `family_id` | `UUID` | `NOT NULL` (inherited from parent token; first token in chain generates a new UUID — Doc 10 §2.5 / N-02) |
| `expires_at` | `TIMESTAMPTZ` | `NOT NULL` (7-day) |
| `revoked_at` | `TIMESTAMPTZ` | `NULL` (set on rotation/logout) |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Indexes:** `idx_refresh_tokens_user_id`; `uq_refresh_tokens_token_hash`; `idx_refresh_tokens_expires_at` (cleanup); `idx_refresh_tokens_family_id` (family-wide revocation on reuse detection).

### 2.4 `password_reset_tokens`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `user_id` | `UUID` | `NOT NULL`, `fk_password_reset_tokens_user_id` → `users(id)` |
| `token_hash` | `VARCHAR(255)` | `NOT NULL`, `UNIQUE` |
| `expires_at` | `TIMESTAMPTZ` | `NOT NULL` (time-limited — REQ-USR-007) |
| `consumed_at` | `TIMESTAMPTZ` | `NULL` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Indexes:** `idx_password_reset_tokens_user_id`; `uq_password_reset_tokens_token_hash`.

### 2.5 `data_exports`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `user_id` | `UUID` | `NOT NULL`, `fk_data_exports_user_id` → `users(id)` |
| `status` | `VARCHAR(16)` | `NOT NULL`, `CHECK (status IN ('REQUESTED','READY','FAILED'))` |
| `download_ref` | `VARCHAR(512)` | `NULL` (object-storage ref when READY) |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Indexes:** `idx_data_exports_user_id`.

---

## 3. Database `category_db`  *(service: `category-service` — Category)*

### 3.1 `categories`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `user_id` | `UUID` | `NULL` (NULL ⇒ DEFAULT/system category, shared; non-null ⇒ Custom owner) |
| `name` | `VARCHAR(80)` | `NOT NULL` |
| `type` | `VARCHAR(8)` | `NOT NULL`, `CHECK (type IN ('EXPENSE','INCOME','BOTH'))` |
| `origin` | `VARCHAR(8)` | `NOT NULL`, `CHECK (origin IN ('DEFAULT','CUSTOM'))` |
| `system_role` | `VARCHAR(8)` | `NOT NULL DEFAULT 'NONE'`, `CHECK (system_role IN ('NONE','SAVINGS'))` |
| `icon` | `VARCHAR(64)` | `NULL` |
| `color` | `VARCHAR(9)` | `NULL` (hex) |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Constraints:**
- `uq_categories_owner_name` — `UNIQUE (user_id, name)` (unique custom name per owner; REQ-CAT-002).
- `ck_categories_default_no_owner` — `CHECK ((origin='DEFAULT' AND user_id IS NULL) OR (origin='CUSTOM' AND user_id IS NOT NULL))`.

**Indexes:** `idx_categories_user_id`; `idx_categories_type`; partial `idx_categories_system_role` `WHERE system_role='SAVINGS'` (fast Savings Category lookup).

> Deletion-when-in-use (REQ-CAT-005) is enforced in the **application** via `CategoryUsagePort` —
> **not** a DB FK, because transactions live in other services (AL-1).

---

## 4. Database `expense_db`  *(service: `expense-service` — Expense / Transaction)*

Tables: `expenses`, `receipts`, `tags`, `expense_tags`, `recurring_expenses`.

### 4.1 `expenses`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `user_id` | `UUID` | `NOT NULL` (owner; SEC-3) |
| `amount` | `NUMERIC(19,4)` | `NOT NULL`, `ck_expenses_amount_positive CHECK (amount > 0)` |
| `currency` | `VARCHAR(3)` | `NOT NULL DEFAULT 'INR'`, `CHECK (currency='INR')` |
| `expense_date` | `DATE` | `NOT NULL` |
| `category_id` | `UUID` | `NOT NULL` (**cross-service ref — no FK**, validated via port) |
| `payment_method` | `VARCHAR(12)` | `NOT NULL`, `CHECK (payment_method IN ('UPI','CASH','CREDIT_CARD','DEBIT_CARD','NET_BANKING','OTHER'))` |
| `description` | `VARCHAR(500)` | `NULL` |
| `merchant` | `VARCHAR(150)` | `NULL` |
| `notes` | `VARCHAR(1000)` | `NULL` |
| `savings_goal_id` | `UUID` | `NULL` (**cross-service ref — no FK**; presence ⇒ Contribution) |
| `recurring_expense_id` | `UUID` | `NULL`, `fk_expenses_recurring_expense_id` → `recurring_expenses(id)` (intra-service FK OK) |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Constraint:** `ck_expenses_contribution_currency` — application-enforced rule that when
`savings_goal_id IS NOT NULL` the `category_id` is the Savings Category (EXP-INV-5); cannot be a DB
FK since `category_id` is cross-service.

**Indexes (DB-4 — list filters REQ-EXP-004/005):**
`idx_expenses_user_id`; `idx_expenses_category_id`; `idx_expenses_payment_method`;
`idx_expenses_savings_goal_id`; `idx_expenses_expense_date`;
composite `idx_expenses_user_date` `(user_id, expense_date DESC)` (default list sort);
`idx_expenses_recurring_expense_id`.

### 4.2 `receipts`  *(local entity, 1:1 with `expenses`)*

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `expense_id` | `UUID` | `NOT NULL`, `UNIQUE` (`uq_receipts_expense_id` — at most one per Expense, EXP-INV-7), `fk_receipts_expense_id` → `expenses(id)` `ON DELETE CASCADE` |
| `user_id` | `UUID` | `NOT NULL` (denormalised owner for fast ownership filter) |
| `storage_ref` | `VARCHAR(512)` | `NOT NULL` (object-storage key; image not in DB) |
| `mime_type` | `VARCHAR(16)` | `NOT NULL`, `CHECK (mime_type IN ('image/jpeg','image/png','image/webp'))` (SEC-5) |
| `size_bytes` | `BIGINT` | `NOT NULL`, `ck_receipts_size_max CHECK (size_bytes <= 5242880)` (5 MB — SEC-5) |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Indexes:** `uq_receipts_expense_id`; `idx_receipts_user_id`.

### 4.3 `tags`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `user_id` | `UUID` | `NOT NULL` |
| `name` | `VARCHAR(50)` | `NOT NULL` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Constraint:** `uq_tags_owner_name` — `UNIQUE (user_id, name)`. **Indexes:** `idx_tags_user_id`.

### 4.4 `expense_tags`  *(join table, many-to-many within service)*

| Column | Type | Constraints |
|--------|------|-------------|
| `expense_id` | `UUID` | `NOT NULL`, `fk_expense_tags_expense_id` → `expenses(id)` `ON DELETE CASCADE` |
| `tag_id` | `UUID` | `NOT NULL`, `fk_expense_tags_tag_id` → `tags(id)` `ON DELETE CASCADE` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Constraint:** `PRIMARY KEY (expense_id, tag_id)`. **Indexes:** PK covers `expense_id`;
add `idx_expense_tags_tag_id` (filter Expenses by Tag — REQ-EXP-004; tag delete detaches — REQ-TAG-003).

### 4.5 `recurring_expenses`  *(template aggregate)*

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `user_id` | `UUID` | `NOT NULL` |
| `amount` | `NUMERIC(19,4)` | `NOT NULL`, `CHECK (amount > 0)` |
| `currency` | `VARCHAR(3)` | `NOT NULL DEFAULT 'INR'` |
| `category_id` | `UUID` | `NOT NULL` (cross-service ref — no FK) |
| `payment_method` | `VARCHAR(12)` | `NOT NULL`, same `CHECK` set as `expenses` |
| `description` | `VARCHAR(500)` | `NULL` |
| `frequency` | `VARCHAR(8)` | `NOT NULL`, `CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','YEARLY'))` |
| `anchor_date` | `DATE` | `NOT NULL` |
| `end_date` | `DATE` | `NULL` |
| `max_occurrences` | `INTEGER` | `NULL`, `CHECK (max_occurrences IS NULL OR max_occurrences > 0)` |
| `generated_count` | `INTEGER` | `NOT NULL DEFAULT 0` |
| `next_run_date` | `DATE` | `NULL` (scheduler cursor) |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Indexes:** `idx_recurring_expenses_user_id`; `idx_recurring_expenses_next_run_date` (scheduler scan — REQ-REC-003).

---

## 5. Database `savings_goal_db`  *(service: `savings-goal-service` — Savings Goal)*

Tables: `savings_goals`, `contribution_entries`.

### 5.1 `savings_goals`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `user_id` | `UUID` | `NOT NULL` |
| `name` | `VARCHAR(120)` | `NOT NULL` |
| `target_amount` | `NUMERIC(19,4)` | `NOT NULL`, `ck_savings_goals_target_positive CHECK (target_amount > 0)` |
| `currency` | `VARCHAR(3)` | `NOT NULL DEFAULT 'INR'`, `CHECK (currency='INR')` |
| `target_date` | `DATE` | `NULL` |
| `description` | `VARCHAR(1000)` | `NULL` |
| `status` | `VARCHAR(12)` | `NOT NULL DEFAULT 'ACTIVE'`, `CHECK (status IN ('ACTIVE','PAUSED','COMPLETED','ABANDONED'))` |
| `total_contributed` | `NUMERIC(19,4)` | `NOT NULL DEFAULT 0`, `CHECK (total_contributed >= 0)` (derived; kept consistent with entries — SG-INV-3) |
| `icon` | `VARCHAR(64)` | `NULL` |
| `color` | `VARCHAR(9)` | `NULL` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Indexes:** `idx_savings_goals_user_id`; composite `idx_savings_goals_user_status` `(user_id, status)` (active/completed split — REQ-GOAL-010).

### 5.2 `contribution_entries`  *(local entity = Contribution History)*

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `savings_goal_id` | `UUID` | `NOT NULL`, `fk_contribution_entries_goal_id` → `savings_goals(id)` `ON DELETE CASCADE` |
| `user_id` | `UUID` | `NOT NULL` |
| `expense_id` | `UUID` | `NOT NULL` (**cross-service ref — no FK**; the backing Expense = source of truth) |
| `amount` | `NUMERIC(19,4)` | `NOT NULL`, `CHECK (amount > 0)` |
| `currency` | `VARCHAR(3)` | `NOT NULL DEFAULT 'INR'` |
| `entry_date` | `DATE` | `NOT NULL` |
| `source` | `VARCHAR(16)` | `NOT NULL`, `CHECK (source IN ('GOAL_SCREEN','LINKED_EXPENSE'))` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Constraint:** `uq_contribution_entries_goal_expense` — `UNIQUE (savings_goal_id, expense_id)`
(one entry per backing Expense — SG-INV-4).
**Indexes:** `idx_contribution_entries_goal_id`; `idx_contribution_entries_user_id`;
`idx_contribution_entries_expense_id` (reconcile on Expense events — REQ-GOAL-007).

---

## 6. Database `budget_db`  *(service: `budget-service` — Budget)*

Tables: `budgets`, `budget_period_ledgers`.

### 6.1 `budgets`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `user_id` | `UUID` | `NOT NULL` |
| `scope` | `VARCHAR(8)` | `NOT NULL`, `CHECK (scope IN ('OVERALL','CATEGORY'))` |
| `category_id` | `UUID` | `NULL` (**cross-service ref — no FK**; required iff scope='CATEGORY') |
| `budget_limit` | `NUMERIC(19,4)` | `NOT NULL`, `ck_budgets_limit_positive CHECK (budget_limit > 0)` (a Budget cannot have a non-positive limit — BUD-INV-1) |
| `currency` | `VARCHAR(3)` | `NOT NULL DEFAULT 'INR'`, `CHECK (currency='INR')` |
| `period_type` | `VARCHAR(8)` | `NOT NULL`, `CHECK (period_type IN ('WEEKLY','MONTHLY'))` |
| `active` | `BOOLEAN` | `NOT NULL DEFAULT true` |
| `rollover_enabled` | `BOOLEAN` | `NOT NULL DEFAULT false` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Constraint:** `ck_budgets_scope_category` — `CHECK ((scope='CATEGORY' AND category_id IS NOT NULL) OR (scope='OVERALL' AND category_id IS NULL))`.
**Indexes:** `idx_budgets_user_id`; `idx_budgets_category_id`; partial `idx_budgets_active` `WHERE active = true` (only active Budgets are evaluated — BUD-INV-7).

### 6.2 `budget_period_ledgers`  *(local entity — one per Budget Period)*

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `budget_id` | `UUID` | `NOT NULL`, `fk_budget_period_ledgers_budget_id` → `budgets(id)` `ON DELETE CASCADE` |
| `user_id` | `UUID` | `NOT NULL` |
| `period_start` | `DATE` | `NOT NULL` |
| `period_end` | `DATE` | `NOT NULL`, `CHECK (period_end >= period_start)` |
| `carried_in` | `NUMERIC(19,4)` | `NOT NULL DEFAULT 0`, `CHECK (carried_in >= 0)` |
| `spent` | `NUMERIC(19,4)` | `NOT NULL DEFAULT 0`, `CHECK (spent >= 0)` (derived from Expense events — BUD-INV-4) |
| `currency` | `VARCHAR(3)` | `NOT NULL DEFAULT 'INR'` |
| `fired_eighty_percent` | `BOOLEAN` | `NOT NULL DEFAULT false` (once-per-period — BUD-INV-5) |
| `fired_exceeded` | `BOOLEAN` | `NOT NULL DEFAULT false` (once-per-period — BUD-INV-5) |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Constraint:** `uq_budget_period_ledgers_budget_window` — `UNIQUE (budget_id, period_start)`
(one ledger per Budget Period).
**Indexes:** `idx_budget_period_ledgers_budget_id`; `idx_budget_period_ledgers_user_id`;
composite `idx_budget_period_ledgers_budget_period` `(budget_id, period_start, period_end)`
(locate the current period fast).

> The `fired_*` boolean pair is the **persisted idempotency guard** enforcing "each Budget Threshold
> fires once per Budget Period" (REQ-BUD-006) — the single most regression-prone rule, made durable
> at the data layer.

---

## 7. Cross-Schema Isolation Verification

### 7.1 Cross-context reference columns (stored as UUID, NO foreign key)

| Column | Lives in (service.table) | Conceptually points to | Validated via (AL-2) |
|--------|--------------------------|--------------------------|----------------------|
| `user_id` | every user-owned table (all services) | `identity_db.users.id` | JWT identity + `UserDataPort` |
| `category_id` | `expense_db.expenses`, `expense_db.recurring_expenses`, `budget_db.budgets` | `category_db.categories.id` | `CategoryLookupPort` |
| `savings_goal_id` | `expense_db.expenses` | `savings_goal_db.savings_goals.id` | `ContributionEventsPort` / `ContributionPort` |
| `expense_id` | `savings_goal_db.contribution_entries` | `expense_db.expenses.id` | `ContributionEventsPort` |

> **None of the above is a database FK.** Each is a bare `UUID NOT NULL/NULL` column. Referential
> integrity across contexts is an **application-layer** concern handled by domain events and ports
> (AL-1/AL-2). FKs exist **only within** a single service's schema (e.g. `receipts.expense_id` →
> `expenses.id`).

### 7.2 Intra-service foreign keys (allowed)

| FK | Within | Cascade |
|----|--------|---------|
| `receipts.expense_id` → `expenses.id` | `expense_db` | `ON DELETE CASCADE` |
| `expense_tags.expense_id` → `expenses.id` | `expense_db` | `ON DELETE CASCADE` |
| `expense_tags.tag_id` → `tags.id` | `expense_db` | `ON DELETE CASCADE` |
| `expenses.recurring_expense_id` → `recurring_expenses.id` | `expense_db` | `ON DELETE SET NULL` |
| `contribution_entries.savings_goal_id` → `savings_goals.id` | `savings_goal_db` | `ON DELETE CASCADE` |
| `budget_period_ledgers.budget_id` → `budgets.id` | `budget_db` | `ON DELETE CASCADE` |
| `email_verifications/refresh_tokens/password_reset_tokens/data_exports.user_id` → `users.id` | `identity_db` | `ON DELETE CASCADE` |

---

## 8. Summary

### 8.1 Tables per database

| Database (service) | Tables | Count |
|--------------------|--------|-------|
| `identity_db` (`user-service`) | users, email_verifications, refresh_tokens, password_reset_tokens, data_exports | 5 |
| `category_db` (`category-service`) | categories | 1 |
| `expense_db` (`expense-service`) | expenses, receipts, tags, expense_tags, recurring_expenses | 5 |
| `savings_goal_db` (`savings-goal-service`) | savings_goals, contribution_entries | 2 |
| `budget_db` (`budget-service`) | budgets, budget_period_ledgers | 2 |
| **Total** | — | **15** |

### 8.2 Index coverage highlights

- **Every `user_id`** across all 15 tables is indexed (ownership filter — DB-4/DB-6).
- **Every cross-service ref column** (`category_id`, `savings_goal_id`, `expense_id`) is indexed.
- **Expense list** is served by `idx_expenses_user_date` (default sort) plus per-filter indexes for
  category, payment method, tag, savings goal, and date (REQ-EXP-004/005).
- **Idempotency / once-per-period** guards persisted: `budget_period_ledgers.fired_*`,
  `uq_contribution_entries_goal_expense`, `uq_receipts_expense_id`.

### 8.3 Notes & assumptions

1. **No cross-schema joins anywhere** (DB-1/DB-2). The §7.1 table is the exhaustive list of
   cross-context references; all are plain UUID columns validated via ports — never FKs.
2. **Audit columns on every table** (`created_at`, `updated_at` `TIMESTAMPTZ NOT NULL`), maintained
   by a per-service `set_updated_at()` trigger (CQ-9).
3. **Money is `NUMERIC(19,4)`** with an `INR`-checked `currency` column everywhere monetary values
   appear (DB-5) — no floating point.
4. **`total_contributed` and ledger `spent` are stored derived values** kept consistent by domain
   events; the authoritative source remains the backing Expense / Expense spending feed (SG-INV-3,
   BUD-INV-4). They are persisted for read performance and idempotent alerting, not as independent
   truth.
5. **Receipt binaries are NOT in PostgreSQL** — only a `storage_ref` to object storage (MinIO),
   with `mime_type` and `size_bytes` validated at the DB layer too (SEC-5).
6. **Scope.** Schemas for the five core services are fully specified. `income_db`,
   `notification_db`, and `reporting_db` follow the identical patterns (own schema, `user_id`-keyed,
   audited, no cross-schema joins) and are deferred to a follow-up; Reporting is read-model only and
   builds its tables from consumed events (no joins back to source schemas — AL-1).

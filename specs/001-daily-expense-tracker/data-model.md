# Data Model — Daily Expense Application (Phase 1)

**Derived from**: [`05-domain-model.md`](./05-domain-model.md) (aggregates, VOs, INV-1..10) and
[`09-data-contract-specification.md`](./09-data-contract-specification.md) (16 tables across 5 DBs + outbox +
processed_events). **Date**: 2026-06-27.

> All names use [`02-glossary.md`](./02-glossary.md) verbatim. Cross-context references are **ID Value
> Objects** in the domain and **bare UUID columns (no cross-service FK)** in the schema, validated via
> Anti-Corruption Ports (AL-1/AL-2). Money is always `NUMERIC(19,4)` + `currency='INR'` (DB-5/INV-6).

## 1. Shared Kernel Value Objects (copied per service, not shared DB)

| VO | Definition | Invariant |
|----|------------|-----------|
| `Money` | INR amount, scale-2 | non-null; never primitive `double`; sign rules per context (Expense/Contribution/Budget/Goal > 0) — INV-6 |
| `AuditTimestamps` | `createdAt`, `updatedAt` | auto-populated on every persisted aggregate — CQ-9 |
| `UserId` | owning General User identity | non-null on every user-owned aggregate; basis of 403-never-404 — INV-1 |
| `DateOnly` | calendar date (Indian locale) | valid date |

## 2. Cross-Cutting Domain Invariants (INV-1..INV-10)

| # | Invariant |
|---|-----------|
| INV-1 | Every user-owned Aggregate Root carries `UserId`; all reads/mutations verify ownership → **403, never 404**. |
| INV-2 | Aggregates reference other aggregates **only by Id Value Objects** — no cross-aggregate/cross-context object refs. |
| INV-3 | Cross-context behaviour flows through Anti-Corruption Ports / events — never another service's repo or schema. |
| INV-4 | A Contribution's authoritative amount is its backing Expense under the Savings Category; `ContributionEntry` is a derived mirror. |
| INV-5 | Each Budget Threshold fires at most once per Budget Period; counters reset next period; rollover seeds next ledger. |
| INV-6 | `Money` is always INR, scale-2, never primitive; Expense/Contribution/Budget amounts strictly positive. |
| INV-7 | Domain/service methods never return `null`; absent lookups return `Optional<T>`. |
| INV-8 | Domain layer holds business logic only; no controller/DTO/repository concern in an Aggregate/Entity/Domain Service. |
| INV-9 | A Default Category (incl. Savings Category) is non-deletable; a Category with transactions cannot be deleted until reassigned. |
| INV-10 | A `RecurringExpense` template is a distinct aggregate from the `Expense` Occurrences it generates. |

## 3. Aggregates by Bounded Context

### 3.1 Identity & Access — `user-service` (`identity_db`)

**Aggregate Root: `User`** — local entities `EmailVerification`, `RefreshToken`, `DataExport`; cannot
authenticate while `INACTIVE_UNVERIFIED`; deletion → `DELETED` + cascade data removal.
VOs: `UserId`, `EmailAddress` (unique, immutable), `PasswordHash` (BCrypt ≥12), `PersonName`,
`UserProfile{preferredCurrency=INR, timezone, locale=en-IN}`. Enum: `AccountStatus{INACTIVE_UNVERIFIED, ACTIVE, DELETED}`.
Domain services: `RegistrationService`, `AuthenticationService`, `TokenRotationService`, `AccountLifecycleService`.

### 3.2 Category — `category-service` (`category_db`)

**Aggregate Root: `Category`** — `owner: UserId?` (null ⇒ Default/system; set ⇒ Custom). VOs: `CategoryId`,
`CategoryName` (unique per owner), `Appearance{icon,color}`. Enums: `CategoryType{EXPENSE,INCOME,BOTH}`,
`CategoryOrigin{DEFAULT,CUSTOM}`, `SystemCategoryRole{NONE,SAVINGS}`. DEFAULT non-deletable (INV-9);
Savings Category = DEFAULT with `systemRole=SAVINGS`. Domain services: `CategoryAuthoringService`,
`CategoryDeletionGuard` (uses `CategoryUsagePort`).

### 3.3 Expense / Transaction — `expense-service` (`expense_db`)

**Aggregate Root: `Expense`** — `amount: Money(>0)`, `date`, `categoryId⇢` (validated via `CategoryLookupPort`),
`paymentMethod`, optional `description/merchant/notes`, `tags: Set<TagId>`, `receipt: Receipt?` (1:1 local),
`savingsGoalId⇢?` (presence ⇒ Contribution; then `categoryId` forced to Savings Category — EXP-INV-5),
`recurringExpenseId⇢?`. No status enum (existence-based). Invariants EXP-INV-1..9.
**Local entities**: `Receipt` (mime ∈ {JPEG,PNG,WEBP}, ≤5 MB, MinIO `storageRef`; ≤1 per Expense — EXP-INV-7);
`Tag` (independent lifecycle; delete detaches without deleting Expenses).
**Aggregate Root: `RecurringExpense`** (template, distinct from Occurrences — INV-10) — `prototype: ExpensePrototype`,
`pattern: RecurrencePattern{DAILY|WEEKLY|MONTHLY|YEARLY, anchorDate}`, `bound: RecurrenceBound{endDate|maxOccurrences|indefinite}`,
`generatedCount`, `nextRunDate`. Occurrence ops: `EditOccurrence`/`EditFromOccurrenceForward`/`DeleteOccurrence`/`DeleteFromOccurrenceForward` (`{id}`=ExpenseId; REC-INV-1..4).
Domain services: `ExpenseService`, `ReceiptService`, `TagManagementService`, `RecurringExpenseGenerator`,
`ExpenseImportService`, `ExpenseExportService`.

### 3.4 Savings Goal — `savings-goal-service` (`savings_goal_db`)

**Aggregate Root: `SavingsGoal`** — `targetAmount: Money(>0)`, optional `targetDate/description/appearance`,
`status: GoalStatus{ACTIVE,PAUSED,COMPLETED,ABANDONED}`, `contributions: List<ContributionEntry>`,
`totalContributed` (= Σ entries, kept consistent with backing Expenses — SG-INV-3). Auto-complete when
`total ≥ target` while ACTIVE (SG-INV-6). Derived (computed, not stored as truth): `GoalProgress`
`{totalContributed, remainingAmount=max(0,target−total), percentAchieved}`, `ProjectedCompletionDate`
(avg monthly rate; undefined when PAUSED). State machine per Doc 06 §3.3 (COMPLETED→ACTIVE only via internal
reconciliation, not API → 409). Invariants SG-INV-1..9.
**Local entity**: `ContributionEntry{expenseId⇢, amount, date, source∈{GOAL_SCREEN,LINKED_EXPENSE}}` (one per backing Expense — SG-INV-4).
Domain services: `ContributionService`, `ContributionReconciliationService`, `GoalLifecycleService`, `GoalProjectionService`.

### 3.5 Budget — `budget-service` (`budget_db`)

**Aggregate Root: `Budget`** — `scope: BudgetScope{OVERALL | CATEGORY(categoryId⇢)}`, `limit: Money(>0)`,
`period: BudgetPeriodType{WEEKLY,MONTHLY}`, `active`, `rolloverEnabled`, `currentLedger`, `pastLedgers`.
Derived: `effectiveLimit = limit + currentLedger.carriedIn`; `remaining = effectiveLimit − spent`; `percentUsed`.
Invariants BUD-INV-1..9.
**Local entity**: `BudgetPeriodLedger{periodWindow{startDate,endDate}, carriedIn, spent, firedThresholds: Set<BudgetThreshold>}`,
`BudgetThreshold∈{EIGHTY_PERCENT,EXCEEDED}`; `spent` derived from `SpendingFeedPort` events (never reads
Expense schema — BUD-INV-4); thresholds fire once per period (BUD-INV-5); rollover seeds next ledger (BUD-INV-8).
Domain services: `BudgetAuthoringService`, `BudgetEvaluationService`, `BudgetRolloverService`, `BudgetStatusService`.

## 4. Physical Schema — 16 tables across 5 databases (Doc 09)

Global rules (DB-1..DB-9): one DB per service; no cross-service FK; every table has `created_at`/`updated_at`
(`TIMESTAMPTZ NOT NULL DEFAULT now()`, `set_updated_at()` trigger); money `NUMERIC(19,4)` + `currency` CHECK
`='INR'`; `user_id UUID NOT NULL` on user-owned tables (indexed); enums as `VARCHAR + CHECK` (UPPER_SNAKE);
PKs are application-generated `UUID`; every filter/join column indexed.

### `identity_db` (5 tables)
- **`users`** — `id`, `full_name`, `email` UQ, `password_hash`, `status` CHECK{INACTIVE_UNVERIFIED,ACTIVE,DELETED},
  `preferred_currency='INR'`, `timezone='Asia/Kolkata'`, `locale='en-IN'`, `weekly_digest_enabled=false`.
  Idx: `uq_users_email`, `idx_users_status`, `idx_users_weekly_digest`.
- **`email_verifications`** — `user_id`(FK→users), `token_hash` UQ, `expires_at`, `consumed_at?`.
- **`refresh_tokens`** — `user_id`(FK), `token_hash` UQ, `family_id` NOT NULL, `expires_at`(7d), `revoked_at?`.
  Idx incl. `idx_refresh_tokens_family_id` (family-wide revoke), `idx_refresh_tokens_expires_at` (cleanup).
- **`password_reset_tokens`** — `user_id`(FK), `token_hash` UQ, `expires_at`, `consumed_at?`.
- **`data_exports`** — `user_id`(FK), `status` CHECK{REQUESTED,READY,FAILED}, `download_ref?` (object-storage ref when READY).

### `category_db` (1 table)
- **`categories`** — `user_id?` (null⇒DEFAULT shared), `name`, `type` CHECK{EXPENSE,INCOME,BOTH},
  `origin` CHECK{DEFAULT,CUSTOM}, `system_role` CHECK{NONE,SAVINGS}, `icon?`, `color?`.
  Constraints: `uq_categories_owner_name (user_id,name)`, `ck_categories_default_no_owner`.
  Idx: `idx_categories_user_id`, `idx_categories_type`, partial `idx_categories_system_role WHERE system_role='SAVINGS'`.
  Deletion-when-in-use enforced in app via `CategoryUsagePort` (not a FK).

### `expense_db` (6 tables)
- **`expenses`** — `user_id`, `amount`(CHECK>0)/`currency`, `expense_date`, `category_id`(xref, no FK),
  `payment_method` CHECK{UPI,CASH,CREDIT_CARD,DEBIT_CARD,NET_BANKING,OTHER}, `description?`, `merchant?`,
  `notes?`, `savings_goal_id?`(xref, no FK; presence⇒Contribution), `recurring_expense_id?`(FK→recurring_expenses).
  Idx: user_id, category_id, payment_method, savings_goal_id, expense_date, composite `idx_expenses_user_date (user_id, expense_date DESC)`, recurring_expense_id.
  App-enforced: when `savings_goal_id` set, `category_id` = Savings Category (EXP-INV-5).
- **`receipts`** — `expense_id` UQ (FK→expenses ON DELETE CASCADE), `user_id` (denormalized), `storage_ref`,
  `mime_type` CHECK{image/jpeg,image/png,image/webp}, `size_bytes` CHECK ≤ 5 242 880.
- **`tags`** — `user_id`, `name`; `uq_tags_owner_name (user_id,name)`.
- **`expense_tags`** — PK `(expense_id, tag_id)`; both FK ON DELETE CASCADE; `idx_expense_tags_tag_id`.
- **`recurring_expenses`** — `user_id`, `amount`(>0)/`currency`, `category_id`(xref), `payment_method`,
  `description?`, `merchant?`, `notes?`, `frequency` CHECK{DAILY,WEEKLY,MONTHLY,YEARLY}, `anchor_date`,
  `end_date?`, `max_occurrences?`(CHECK>0), `generated_count=0`, `next_run_date?`. Idx incl. `idx_recurring_expenses_next_run_date` (scheduler scan).
- **`recurring_expense_tags`** — PK `(recurring_expense_id, tag_id)`; both FK ON DELETE CASCADE (tags carried to generated occurrences).

### `savings_goal_db` (2 tables)
- **`savings_goals`** — `user_id`, `name`, `target_amount`(CHECK>0)/`currency`, `target_date?`, `description?`,
  `status` CHECK{ACTIVE,PAUSED,COMPLETED,ABANDONED} default ACTIVE, `total_contributed`(CHECK≥0, derived),
  `icon?`, `color?`. Idx: user_id, composite `idx_savings_goals_user_status (user_id,status)`.
- **`contribution_entries`** — `savings_goal_id`(FK→savings_goals CASCADE), `user_id`, `expense_id`(xref, no FK),
  `amount`(>0)/`currency`, `entry_date`, `source` CHECK{GOAL_SCREEN,LINKED_EXPENSE}.
  `uq_contribution_entries_goal_expense (savings_goal_id, expense_id)` (one entry per backing Expense — SG-INV-4).
  Idx incl. `idx_contribution_entries_expense_id` (reconcile on Expense events).

### `budget_db` (2 tables)
- **`budgets`** — `user_id`, `scope` CHECK{OVERALL,CATEGORY}, `category_id?`(xref, required iff CATEGORY),
  `budget_limit`(CHECK>0)/`currency`, `period_type` CHECK{WEEKLY,MONTHLY}, `active=true`, `rollover_enabled=false`.
  `ck_budgets_scope_category`; idx incl. partial `idx_budgets_active WHERE active=true`.
- **`budget_period_ledgers`** — `budget_id`(FK→budgets CASCADE), `user_id`, `period_start`, `period_end`(CHECK≥start),
  `carried_in`(≥0), `spent`(≥0, derived), `fired_eighty_percent=false`, `fired_exceeded=false`.
  `uq_budget_period_ledgers_budget_window (budget_id, period_start)`. The two `fired_*` booleans **are** the
  `FiredThresholdSet` VO (true ≡ in set) — the persisted once-per-period idempotency guard (BUD-INV-5/DAT-006).

## 5. Infrastructure Tables (per service — Doc 09 §7)

- **`outbox`** — `id`, `event_id` UQ (idempotency key = `EventEnvelope.eventId`), `aggregate_type`,
  `aggregate_id`, `event_type`, `payload` JSONB, `published=false`, `created_at`, `published_at?`.
  Written in the **same transaction** as the state change (CQ-8); relay polls `idx_outbox_published_created`,
  publishes to Kafka, marks published.
- **`processed_events`** — `event_id` PK, `event_type`, `processed_at`. Consumer inserts before processing;
  existing row ⇒ skip (duplicate-delivery guard). Pruned > 30 days (≤ Kafka retention).

## 6. Cross-Context Reference Columns (UUID, NO foreign key — Doc 09 §8.1)

| Column | Lives in | Conceptually points to | Validated via (AL-2) |
|--------|----------|--------------------------|----------------------|
| `user_id` | every user-owned table | `identity_db.users.id` | JWT identity + `UserDataPort` |
| `category_id` | `expenses`, `recurring_expenses`, `budgets` | `category_db.categories.id` | `CategoryLookupPort` |
| `savings_goal_id` | `expenses` | `savings_goal_db.savings_goals.id` | `ContributionPort`/`ContributionEventsPort` |
| `expense_id` | `contribution_entries` | `expense_db.expenses.id` | `ContributionEventsPort` |

Intra-service FKs are allowed (e.g. `receipts.expense_id→expenses.id` CASCADE; `budget_period_ledgers.budget_id→budgets.id` CASCADE).

## 7. Anti-Corruption Ports (cross-context channels — Doc 05 §8)

`CategoryLookupPort` (Expense,Budget→Category), `CategoryUsagePort` (Category→Expense), `ContributionPort`
(Savings Goal→Expense), `ContributionEventsPort` (Expense→Savings Goal), `SpendingFeedPort` (Budget→Expense),
`NotificationPort` (Savings Goal,Budget,Expense→Notification — **Phase 1: outbox only, no consumer**),
`UserDataPort` (Identity→all), `SecureNotificationDeliveryPort` (Notification→Identity, resolves opaque `deliveryRef`).

## 8. Summary

| Database (service) | Tables | Count |
|--------------------|--------|-------|
| `identity_db` (user-service) | users, email_verifications, refresh_tokens, password_reset_tokens, data_exports | 5 |
| `category_db` (category-service) | categories | 1 |
| `expense_db` (expense-service) | expenses, receipts, tags, expense_tags, recurring_expenses, recurring_expense_tags | 6 |
| `savings_goal_db` (savings-goal-service) | savings_goals, contribution_entries | 2 |
| `budget_db` (budget-service) | budgets, budget_period_ledgers | 2 |
| **Total domain tables** | | **16** |
| + per service | `outbox`, `processed_events` | (×5) |

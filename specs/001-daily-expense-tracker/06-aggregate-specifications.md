# Aggregate Specifications — Daily Expense Application

| Field | Value |
|-------|-------|
| **Document** | 06 — Aggregate Specifications (`Expense`, `SavingsGoal`, `Budget`) |
| **Feature** | Daily Expense Application |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Status** | Draft |
| **Created** | 2026-06-25 |
| **Author Role** | Senior Java / Spring Backend Architect |
| **Source Inputs** | `05-domain-model.md`, `.specify/memory/constitution.md` (v1.1.1) |
| **Governing Authority** | [Daily Expense Application — Engineering Constitution](../../.specify/memory/constitution.md) |
| **Vocabulary Authority** | [Ubiquitous Language Glossary](./02-glossary.md) |
| **Domain Authority** | [Domain Model Specification](./05-domain-model.md) |
| **Traceability Authority** | [Requirement Catalogue](./03-requirement-catalogue.md) |

> **Purpose.** Specify the **lifecycle, state, invariants, commands, and domain events** for the
> three most complex aggregates identified in `05-domain-model.md`: **`Expense`**, **`SavingsGoal`**,
> and **`Budget`**. This is the tactical contract a Spring Boot implementation must satisfy. All
> names use the [Glossary](./02-glossary.md) and Constitution §6 naming conventions verbatim.

---

## 1. Conventions

### 1.1 Naming (Constitution §6)

| Artefact | Convention | Example |
|----------|------------|---------|
| Aggregate / JPA entity | singular `PascalCase` | `Expense`, `SavingsGoal`, `Budget` |
| Command object | `PascalCase` + `Command` | `CreateExpenseCommand` |
| Domain event | `PascalCase` + `Event` (past tense) | `ExpenseCreatedEvent` |
| Enum | singular `PascalCase`; values `UPPER_SNAKE_CASE` | `PaymentMethod.UPI` |
| Value Object | `PascalCase` | `Money`, `BudgetThreshold` |

### 1.2 Modelling laws applied

- **Layers are Sacred (P3 / CQ-1).** State mutation happens **inside the aggregate**; the
  application/domain **service** invokes commands and publishes the resulting events. Controllers,
  DTOs, and repositories are out of scope here.
- **Service Isolation (AL-1/AL-2).** Cross-context links are **Id Value Objects** only
  (`UserId`, `CategoryId`, `SavingsGoalId`, `ExpenseId`). Aggregates emit **domain events**;
  cross-context reactions are handled by the consuming service via its Anti-Corruption Port.
- **No nulls (CQ-2).** Optional state uses `Optional<T>`; commands reject invalid input before
  mutation (fail-closed). **Ownership (P4 / SEC-3)** is checked by the service before any command
  executes — foreign access yields **403, never 404**.
- **Money (Glossary).** All amounts are `Money` (INR, scale-2, never primitive `double`).
- **Transactions (CQ-8).** Each command executes within a single write transaction; events publish
  after commit.

### 1.3 Command / Event contract

> A **Command** is an intent to change one aggregate instance; it is validated against the
> aggregate's invariants and either mutates state (emitting one or more **Domain Events**) or is
> rejected. A **Domain Event** is an immutable, past-tense fact carrying the minimal payload
> (Ids + changed values) needed by subscribers — never a serialized entity (AL-4).

---

## 2. Aggregate: `Expense`  *(context: Expense / Transaction — `expense-service`)*

The core transaction (money out). It optionally backs a **Contribution** (when `savingsGoalId` is
present) and optionally derives from a **RecurringExpense** template.

### 2.1 State / Properties

| Property | Type («VO»/«enum») | Required | Notes |
|----------|--------------------|----------|-------|
| `expenseId` | `ExpenseId` | yes | Identity; immutable. |
| `userId` | `UserId` | yes | Owner; ownership key (SEC-3); immutable. |
| `amount` | `Money` | yes | INR, strictly > 0. |
| `date` | `DateOnly` | yes | Indian locale/format. |
| `categoryId` | `CategoryId` | yes | Cross-context ref by Id; validated via `CategoryLookupPort`. |
| `paymentMethod` | `PaymentMethod` | yes | `{ UPI, CASH, CREDIT_CARD, DEBIT_CARD, NET_BANKING, OTHER }`. |
| `description` | `Optional<Description>` | no | Free text. |
| `merchant` | `Optional<MerchantName>` | no | Expense-side counterpart of Income's `Source`. |
| `notes` | `Optional<Notes>` | no | Free text. |
| `tags` | `Set<TagId>` | no | Cross-aggregate Tag references by TagId (AL-1); Tag is an Entity managed by TagManagementService. May be empty, never null. |
| `receipt` | `Optional<Receipt>` | no | Local entity: `{ mimeType ∈ {JPEG,PNG,WEBP}, size ≤ 5 MB, storageRef }`. |
| `savingsGoalId` | `Optional<SavingsGoalId>` | no | Presence ⇒ this Expense is a **Contribution**. |
| `recurringExpenseId` | `Optional<RecurringExpenseId>` | no | Set when generated as an **Occurrence**. |
| `audit` | `AuditTimestamps` | yes | `createdAt` / `updatedAt` auto-populated (CQ-9). |

> **Note — `Expense` has no explicit status enum.** Its lifecycle is *existence-based* (Created →
> Modified* → Deleted). A "Contribution" is not a separate state but the condition `savingsGoalId
> is present` combined with `categoryId = Savings Category`.

### 2.2 Invariants (must ALWAYS hold)

| # | Invariant | Source |
|---|-----------|--------|
| EXP-INV-1 | `amount` is a `Money` in INR with scale 2 and value strictly **> 0**. | Glossary, REQ-EXP-001 |
| EXP-INV-2 | `date`, `categoryId`, and `paymentMethod` are always present. | REQ-EXP-001 |
| EXP-INV-3 | `userId` is immutable for the life of the Expense (an Expense never changes owner). | P4, SEC-3 |
| EXP-INV-4 | `categoryId` must reference a Category typed `EXPENSE` or `BOTH` and visible to `userId`. | REQ-CAT-004 |
| EXP-INV-5 | When `savingsGoalId` is present (Expense is a **Contribution**), `categoryId` **must** be the system **Savings Category**. | REQ-GOAL-004, Glossary |
| EXP-INV-6 | A `Receipt`, if present, has `mimeType ∈ {JPEG, PNG, WEBP}` and `size ≤ 5 MB`. | REQ-SEC-005 |
| EXP-INV-7 | At most **one** `Receipt` is attached at a time. | REQ-EXP-009 |
| EXP-INV-8 | `tags` is a set (no duplicates); deleting a canonical Tag detaches it here without deleting the Expense. | REQ-TAG-002/003 |
| EXP-INV-9 | Any change to `amount`, `date`, or `savingsGoalId` of a Contribution-backing Expense **must** emit an event so the linked `SavingsGoal` total reconciles. | REQ-EXP-007/008, REQ-GOAL-007 |

### 2.3 Lifecycle

```text
            CreateExpenseCommand
                   │  (validate EXP-INV-1..5)
                   ▼
            ┌──────────────┐   UpdateExpenseDetailsCommand / AttachReceipt / RemoveReceipt
            │   EXISTING    │◀──── LinkExpenseToSavingsGoalCommand / UnlinkExpenseFromSavingsGoalCommand
            │   (active)    │────┐ ApplyTagsCommand / RemoveTagCommand
            └──────┬───────┘    │ (each mutation bumps updatedAt; may emit reconcile event)
                   │            └────────────────┐
       DeleteExpenseCommand                       │
                   ▼                              ▼
            ┌──────────────┐              (events published post-commit)
            │   DELETED     │  ⇒ if was a Contribution: emit ExpenseDeletedEvent → goal total decreases
            └──────────────┘
```

### 2.4 Commands

| Command | Preconditions / guards | State change | Events emitted |
|---------|------------------------|--------------|----------------|
| `CreateExpenseCommand` | Owner authenticated; EXP-INV-1..5 satisfied; `categoryId` valid via port. | New `Expense` created. | `ExpenseCreatedEvent`; if `savingsGoalId` present → also `ExpenseLinkedToSavingsGoalEvent`. |
| `UpdateExpenseDetailsCommand` | Caller owns the Expense (else 403). Re-validate invariants. | Mutates amount/date/category/paymentMethod/description/merchant/notes. | `ExpenseUpdatedEvent`; if amount/date changed **and** linked → `ContributionAmountAdjustedEvent`. |
| `AttachReceiptCommand` | Owns Expense; EXP-INV-6/7 (type/size, ≤ 1 receipt). | Sets `receipt`. | `ReceiptAttachedEvent`. |
| `RemoveReceiptCommand` | Owns Expense; a `Receipt` exists. | Clears `receipt`. | `ReceiptRemovedEvent`. |
| `LinkExpenseToSavingsGoalCommand` | Owns Expense; goal owned by same user (via port); on link `categoryId` set to Savings Category. | Sets `savingsGoalId`. | `ExpenseLinkedToSavingsGoalEvent`. |
| `UnlinkExpenseFromSavingsGoalCommand` | Owns Expense; currently linked. | Clears `savingsGoalId`; remains a regular Expense. | `ExpenseUnlinkedFromSavingsGoalEvent`. |
| `ApplyTagsCommand` / `RemoveTagCommand` | Owns Expense. | Adds/removes `Tag` from the set. | `ExpenseTagsChangedEvent`. |
| `DeleteExpenseCommand` | Owns Expense. | Marks Expense deleted (removes record + Receipt). | `ExpenseDeletedEvent`; if was linked → carries `savingsGoalId` so goal total decreases. |

### 2.5 Domain Events

| Event | Payload (minimal; Ids + changed values) | Primary subscribers |
|-------|------------------------------------------|---------------------|
| `ExpenseCreatedEvent` | `expenseId, userId, categoryId, amount, date, paymentMethod, savingsGoalId?` | Budget (spending feed), Reporting, Notification (recurring-failure n/a). |
| `ExpenseUpdatedEvent` | `expenseId, userId, changed fields (amount?, date?, categoryId?)` | Budget, Reporting. |
| `ExpenseDeletedEvent` | `expenseId, userId, categoryId, amount, date, savingsGoalId?` | Budget, Reporting, Savings Goal (decrement). |
| `ExpenseLinkedToSavingsGoalEvent` | `expenseId, userId, savingsGoalId, amount, date` | Savings Goal (add Contribution). |
| `ExpenseUnlinkedFromSavingsGoalEvent` | `expenseId, userId, savingsGoalId, amount` | Savings Goal (remove Contribution). |
| `ContributionAmountAdjustedEvent` | `expenseId, userId, savingsGoalId, oldAmount, newAmount, newDate` | Savings Goal (reconcile total). |
| `ReceiptAttachedEvent` / `ReceiptRemovedEvent` | `expenseId, userId` | Reporting/metrics (optional). |
| `ExpenseTagsChangedEvent` | `expenseId, userId, addedTags, removedTags` | Reporting (optional). |

> **Budget linkage.** `ExpenseCreatedEvent` / `ExpenseUpdatedEvent` / `ExpenseDeletedEvent` are the
> `SpendingFeedPort` inputs that drive `Budget` threshold evaluation (§4) — Budget never reads the
> Expense schema (AL-1).

---

## 3. Aggregate: `SavingsGoal`  *(context: Savings Goal — `savings-goal-service`)*

Tracks a target, its **Contribution History**, lifecycle status, and derived progress. The
authoritative amount of each Contribution lives in the backing `Expense`; this aggregate holds the
*link and running total*.

### 3.1 State / Properties

| Property | Type | Required | Notes |
|----------|------|----------|-------|
| `savingsGoalId` | `SavingsGoalId` | yes | Identity; immutable. |
| `userId` | `UserId` | yes | Owner; immutable. |
| `name` | `GoalName` | yes | e.g. "MacBook Pro". |
| `targetAmount` | `Money` | yes | INR, strictly > 0. |
| `targetDate` | `Optional<DateOnly>` | no | User intent; distinct from derived `ProjectedCompletionDate`. |
| `description` | `Optional<Description>` | no | — |
| `appearance` | `Optional<Appearance>` | no | `{ icon, color }`. |
| `status` | `GoalStatus` | yes | `{ ACTIVE, PAUSED, COMPLETED, ABANDONED }`. |
| `contributions` | `List<ContributionEntry>` | yes | The **Contribution History** (may be empty, never null). |
| `totalContributed` | `Money` | yes (derived/stored) | Σ of `contributions.amount`; kept consistent with backing Expenses. |
| `audit` | `AuditTimestamps` | yes | CQ-9. |

`ContributionEntry` (local entity): `{ contributionEntryId, expenseId: ExpenseId, amount: Money,
date: DateOnly, source ∈ { GOAL_SCREEN, LINKED_EXPENSE } }`.

**Derived Value Objects (computed, not stored as truth):**
`GoalProgress { totalContributed, remainingAmount = max(0, targetAmount − totalContributed),
percentAchieved }`; `ProjectedCompletionDate` (from average monthly contribution rate; **undefined
when `PAUSED`**).

### 3.2 Invariants

| # | Invariant | Source |
|---|-----------|--------|
| SG-INV-1 | `targetAmount` is `Money` in INR with value strictly **> 0**. | REQ-GOAL-001 |
| SG-INV-2 | `userId` is immutable; goal is private to its owner. | P4, SEC-3 |
| SG-INV-3 | `totalContributed` **always equals** the sum of `contributions[*].amount`, and each entry mirrors an existing backing `Expense` under the **Savings Category**. | REQ-GOAL-006/007, Glossary |
| SG-INV-4 | Every `ContributionEntry.amount` is `Money` INR **> 0**; `expenseId` is unique within `contributions` (one entry per backing Expense). | REQ-GOAL-004/005 |
| SG-INV-5 | `status` transitions are restricted to the lifecycle in §3.3; terminal states (`COMPLETED`, `ABANDONED`) only re-open via explicit re-activation rules below. | REQ-GOAL-011/012/013 |
| SG-INV-6 | When `totalContributed ≥ targetAmount` and `status = ACTIVE`, the goal **auto-transitions** to `COMPLETED` and emits a completion event. | REQ-GOAL-011 |
| SG-INV-7 | A `PAUSED` goal is excluded from `ProjectedCompletionDate` and the active goals list, but its `contributions` history is fully preserved. | REQ-GOAL-013 |
| SG-INV-8 | Deleting the goal detaches its backing Expenses (they lose `savingsGoalId`) but does **not** delete them. | REQ-GOAL-003 |
| SG-INV-9 | `remainingAmount` is never negative (floored at 0). | REQ-GOAL-008 |

### 3.3 Lifecycle (state machine)

```text
                 CreateSavingsGoalCommand
                         │
                         ▼
                   ┌──────────┐  RecordContribution / LinkExistingExpense / ReconcileContribution
        ┌─────────▶│  ACTIVE   │◀──────────── ResumeSavingsGoalCommand
        │          └────┬──────┘
        │   PauseGoal   │  totalContributed ≥ targetAmount (auto, SG-INV-6)
        │               │            │
        │               ▼            ▼
        │          ┌──────────┐   ┌────────────┐
        └──────────│  PAUSED   │   │ COMPLETED   │  (auto or MarkGoalCompletedCommand)
                   └────┬──────┘   └─────┬──────┘
                        │ Resume          │ (terminal; re-opens to ACTIVE only via
                        ▼                 ▼   ReconcileContributionCommand when totalContributed
                   (back to ACTIVE)   ┌────────────┐  drops below targetAmount — NOT via direct API call)
                                      │ ABANDONED   │  (MarkGoalAbandonedCommand; terminal)
                                      └────────────┘

Note: ABANDONED is reachable from ACTIVE or PAUSED and is always terminal (no re-open path).
COMPLETED may revert to ACTIVE only via ReconcileContributionCommand (event-driven reconciliation
triggered by a backing-Expense edit/delete — SG-INV-3); this is never a direct API call.
PATCH /savings-goals/{id}/status returns 409 Conflict for COMPLETED → ACTIVE and ABANDONED → ACTIVE.
```

### 3.4 Commands

| Command | Preconditions / guards | State change | Events emitted |
|---------|------------------------|--------------|----------------|
| `CreateSavingsGoalCommand` | Owner authenticated; SG-INV-1. | New goal in `ACTIVE`. | `SavingsGoalCreatedEvent`. |
| `UpdateSavingsGoalCommand` | Owns goal; SG-INV-1 (target stays > 0). | Edits name/target/targetDate/description/appearance. | `SavingsGoalUpdatedEvent`. |
| `RecordContributionCommand` *(primary flow)* | Owns goal; amount > 0, date present. Instructs Expense ctx (via `ContributionPort`) to create backing Expense under Savings Category; appends `ContributionEntry(source=GOAL_SCREEN)`; recomputes total. | `totalContributed` increases; may trigger SG-INV-6. | `ContributionRecordedEvent`; possibly `SavingsGoalCompletedEvent`. |
| `LinkExistingExpenseCommand` *(secondary flow)* | Owns goal; the Expense is owned by same user. Appends `ContributionEntry(source=LINKED_EXPENSE)`. | `totalContributed` increases. | `ContributionRecordedEvent`; possibly `SavingsGoalCompletedEvent`. |
| `ReconcileContributionCommand` | Triggered by `ContributionAmountAdjustedEvent` / `ExpenseDeletedEvent` / `ExpenseUnlinkedFromSavingsGoalEvent` from Expense ctx. | Updates/removes matching `ContributionEntry`; recomputes total; may revert `COMPLETED → ACTIVE`. | `ContributionAdjustedEvent` or `ContributionRemovedEvent`; possibly `SavingsGoalReopenedEvent`. |
| `PauseSavingsGoalCommand` | Owns goal; `status = ACTIVE`. | `status = PAUSED`. | `SavingsGoalPausedEvent`. |
| `ResumeSavingsGoalCommand` | Owns goal; `status = PAUSED`. COMPLETED and ABANDONED are not valid source states for this command; the service returns 409 for those. COMPLETED → ACTIVE is driven only by `ReconcileContributionCommand` (see §3.3 Note). | `status = ACTIVE`. | `SavingsGoalResumedEvent`. |
| `MarkGoalCompletedCommand` | Owns goal; manual completion allowed regardless of total. | `status = COMPLETED`. | `SavingsGoalCompletedEvent`. |
| `MarkGoalAbandonedCommand` | Owns goal. | `status = ABANDONED` (history preserved). | `SavingsGoalAbandonedEvent`. |
| `DeleteSavingsGoalCommand` | Owns goal. | Detaches backing Expenses (SG-INV-8); removes goal. | `SavingsGoalDeletedEvent` (carries `expenseId` list to unlink). |

### 3.5 Domain Events

| Event | Payload | Primary subscribers |
|-------|---------|---------------------|
| `SavingsGoalCreatedEvent` | `savingsGoalId, userId, name, targetAmount, targetDate?` | Reporting/Dashboard. |
| `SavingsGoalUpdatedEvent` | `savingsGoalId, userId, changed fields` | Reporting. |
| `ContributionRecordedEvent` | `savingsGoalId, userId, expenseId, amount, date, source` | Dashboard, Reporting. |
| `ContributionAdjustedEvent` | `savingsGoalId, userId, expenseId, oldAmount, newAmount` | Dashboard, Reporting. |
| `ContributionRemovedEvent` | `savingsGoalId, userId, expenseId, amount` | Dashboard, Reporting. |
| `SavingsGoalCompletedEvent` | `savingsGoalId, userId, totalContributed, completedAutomatically` | **Notification** (in-app), Dashboard. |
| `SavingsGoalReopenedEvent` | `savingsGoalId, userId, totalContributed` | Dashboard. |
| `SavingsGoalPausedEvent` / `SavingsGoalResumedEvent` / `SavingsGoalAbandonedEvent` | `savingsGoalId, userId` | Dashboard. |
| `SavingsGoalDeletedEvent` | `savingsGoalId, userId, detachedExpenseIds[]` | **Expense ctx** (unlink), Dashboard. |

> **Completion → Notification.** `SavingsGoalCompletedEvent` is consumed by the Notification context
> (`NotificationPort`) to raise the in-app "goal Completed" Notification (REQ-GOAL-011, REQ-NOTIF-002).

---

## 4. Aggregate: `Budget`  *(context: Budget — `budget-service`)*

Holds a spending limit for an `OVERALL` scope or a single `CategoryId`, evaluated per **Budget
Period**, with optional **Rollover** and once-per-period threshold alerting.

### 4.1 State / Properties

| Property | Type | Required | Notes |
|----------|------|----------|-------|
| `budgetId` | `BudgetId` | yes | Identity; immutable. |
| `userId` | `UserId` | yes | Owner; immutable. |
| `scope` | `BudgetScope` | yes | `OVERALL` or `CATEGORY(categoryId)`. |
| `limit` | `Money` | yes | INR, strictly > 0 — the amount set. |
| `period` | `BudgetPeriodType` | yes | `{ WEEKLY, MONTHLY }`. |
| `active` | `boolean` | yes | Activate/deactivate without delete. |
| `rolloverEnabled` | `boolean` | yes | Carry unspent into next period. |
| `currentLedger` | `BudgetPeriodLedger` | yes | The ledger for the active Budget Period. |
| `pastLedgers` | `List<BudgetPeriodLedger>` | yes | History (may be empty). |
| `audit` | `AuditTimestamps` | yes | CQ-9. |

`BudgetPeriodLedger` (local entity): `{ periodWindow: PeriodWindow{startDate,endDate},
carriedIn: Money, spent: Money, firedThresholds: Set<BudgetThreshold> }` where
`BudgetThreshold ∈ { EIGHTY_PERCENT, EXCEEDED }`.

**Derived:** `effectiveLimit = limit + currentLedger.carriedIn`; `remaining = effectiveLimit −
currentLedger.spent`; `percentUsed = currentLedger.spent / effectiveLimit × 100`.

### 4.2 Invariants

| # | Invariant | Source |
|---|-----------|--------|
| BUD-INV-1 | `limit` is `Money` in INR strictly **> 0** (a Budget cannot have a zero or negative limit). | REQ-BUD-001 |
| BUD-INV-2 | `userId` is immutable; Budget is private to its owner. | P4, SEC-3 |
| BUD-INV-3 | If `scope = CATEGORY`, `categoryId` references a Category visible to `userId`; `OVERALL` has no `categoryId`. | REQ-BUD-001, AL-1 |
| BUD-INV-4 | `currentLedger.spent ≥ 0` and is derived solely from `SpendingFeedPort` data (Budget never reads Expense schema). | REQ-BUD-007, AL-1 |
| BUD-INV-5 | Each `BudgetThreshold` fires **at most once per Budget Period** — `firedThresholds` is a set; a threshold already present is never re-alerted. | REQ-BUD-006 |
| BUD-INV-6 | The `EXCEEDED` threshold can only be fired in a period where `spent ≥ effectiveLimit`; `EIGHTY_PERCENT` only where `spent ≥ 0.8 × effectiveLimit`. | REQ-BUD-004/005 |
| BUD-INV-7 | When `active = false`, **no** Budget Alert is ever fired; the Budget is retained for reactivation. | REQ-BUD-002 |
| BUD-INV-8 | On Budget Period rollover, a new `currentLedger` is opened; if `rolloverEnabled`, its `carriedIn = max(0, prior effectiveLimit − prior spent)`, else `carriedIn = 0`. `firedThresholds` resets (empty). | REQ-BUD-003/006 |
| BUD-INV-9 | A `BudgetThreshold` value is one of `{ EIGHTY_PERCENT, EXCEEDED }` — there is no negative or arbitrary threshold. | REQ-BUD-004/005 |

### 4.3 Lifecycle

```text
              CreateBudgetCommand (BUD-INV-1,3)
                       │
                       ▼
                 ┌───────────┐  ActivateBudget / DeactivateBudget  (toggles `active`)
        ┌───────▶│  ACTIVE    │◀──── EnableRollover / DisableRollover
        │        │ (evaluating)│
        │        └─────┬──────┘
        │  RecordSpendingObservation (from SpendingFeedPort)
        │              │  ├─ crosses 80%  → fire EIGHTY_PERCENT  (once, BUD-INV-5)
        │              │  └─ crosses 100% → fire EXCEEDED        (once, BUD-INV-5)
        │              ▼
        │     RollOverBudgetPeriodCommand (period boundary)
        │              │  open new ledger; carry unspent if enabled; reset firedThresholds
        │              ▼
        │        ┌───────────┐
        └────────│ DEACTIVATED│  (active=false; no alerts — BUD-INV-7; reactivatable)
                 └─────┬──────┘
                       │ DeleteBudgetCommand
                       ▼
                 ┌───────────┐
                 │  DELETED   │
                 └───────────┘
```

### 4.4 Commands

| Command | Preconditions / guards | State change | Events emitted |
|---------|------------------------|--------------|----------------|
| `CreateBudgetCommand` | Owner authenticated; BUD-INV-1/3; opens first `currentLedger`. | New `Budget` (`active=true`). | `BudgetCreatedEvent`. |
| `UpdateBudgetCommand` | Owns Budget; BUD-INV-1 (limit > 0). | Edits `limit`, `period`, `scope`. | `BudgetUpdatedEvent`. |
| `ActivateBudgetCommand` / `DeactivateBudgetCommand` | Owns Budget. | Toggles `active`. | `BudgetActivatedEvent` / `BudgetDeactivatedEvent`. |
| `EnableRolloverCommand` / `DisableRolloverCommand` | Owns Budget. | Toggles `rolloverEnabled`. | `BudgetRolloverConfigChangedEvent`. |
| `RecordSpendingObservationCommand` | `active=true`; observation within current `periodWindow`; sourced from `SpendingFeedPort`. Updates `spent`; evaluates BUD-INV-6 against thresholds not yet in `firedThresholds`. | `currentLedger.spent` updated; thresholds may fire. | `BudgetSpendingUpdatedEvent`; if 80% first crossed → `BudgetThresholdReachedEvent(EIGHTY_PERCENT)`; if exceeded first crossed → `BudgetExceededEvent`. |
| `RollOverBudgetPeriodCommand` | Period boundary reached. | Archive `currentLedger` → `pastLedgers`; open new ledger (BUD-INV-8). | `BudgetPeriodRolledOverEvent`. |
| `DeleteBudgetCommand` | Owns Budget. | Marks deleted. | `BudgetDeletedEvent`. |

### 4.5 Domain Events

| Event | Payload | Primary subscribers |
|-------|---------|---------------------|
| `BudgetCreatedEvent` | `budgetId, userId, scope, limit, period, rolloverEnabled` | Dashboard, Reporting. |
| `BudgetUpdatedEvent` | `budgetId, userId, changed fields` | Dashboard. |
| `BudgetActivatedEvent` / `BudgetDeactivatedEvent` | `budgetId, userId` | Dashboard. |
| `BudgetRolloverConfigChangedEvent` | `budgetId, userId, rolloverEnabled` | Dashboard. |
| `BudgetSpendingUpdatedEvent` | `budgetId, userId, periodWindow, spent, percentUsed` | Dashboard (status). |
| `BudgetThresholdReachedEvent` | `budgetId, userId, threshold=EIGHTY_PERCENT, periodWindow, spent, effectiveLimit` | **Notification** (in-app + email). |
| `BudgetExceededEvent` | `budgetId, userId, threshold=EXCEEDED, periodWindow, spent, effectiveLimit` | **Notification** (in-app + email). |
| `BudgetPeriodRolledOverEvent` | `budgetId, userId, newPeriodWindow, carriedIn` | Dashboard, Reporting. |
| `BudgetDeletedEvent` | `budgetId, userId` | Dashboard. |

> **Breach → Notification (once per period per threshold).** `BudgetThresholdReachedEvent` and
> `BudgetExceededEvent` are consumed by the Notification context to deliver the **Budget Alert** as
> both an in-app Notification and an email (REQ-BUD-004/005, REQ-NOTIF-002). Idempotency is
> guaranteed by `firedThresholds` (BUD-INV-5), so a redelivered spending observation never produces
> a duplicate alert.

---

## 5. Cross-Aggregate Event Flow (the Contribution & Budget loops)

```text
Contribution loop (Expense ↔ Savings Goal — Partnership):
  RecordContributionCommand (SavingsGoal)
      └─▶ ContributionPort.createBackingExpense()  →  CreateExpenseCommand (Expense, Savings Category)
                                                          └─▶ ExpenseCreatedEvent
      └─▶ ContributionRecordedEvent  →  (if total ≥ target) SavingsGoalCompletedEvent → Notification

  Edit/delete backing Expense (Expense):
      UpdateExpenseDetailsCommand / DeleteExpenseCommand
          └─▶ ContributionAmountAdjustedEvent / ExpenseDeletedEvent
                  └─▶ ReconcileContributionCommand (SavingsGoal) → total recomputed (may reopen)

Budget loop (Expense → Budget → Notification):
  ExpenseCreatedEvent / ExpenseUpdatedEvent / ExpenseDeletedEvent
      └─▶ SpendingFeedPort  →  RecordSpendingObservationCommand (Budget)
              ├─▶ BudgetThresholdReachedEvent(80%)  →  Notification (Budget Alert: in-app + email)
              └─▶ BudgetExceededEvent               →  Notification (Budget Alert: in-app + email)
```

All cross-context arrows cross a **service boundary** and travel via published events / Anti-Corruption
Ports — never a shared repository or schema (AL-1/AL-2).

---

## 6. Summary

### 6.1 Commands per aggregate

| Aggregate | Commands |
|-----------|----------|
| `Expense` | CreateExpense, UpdateExpenseDetails, AttachReceipt, RemoveReceipt, LinkExpenseToSavingsGoal, UnlinkExpenseFromSavingsGoal, ApplyTags, RemoveTag, DeleteExpense |
| `SavingsGoal` | CreateSavingsGoal, UpdateSavingsGoal, RecordContribution, LinkExistingExpense, ReconcileContribution, PauseSavingsGoal, ResumeSavingsGoal, MarkGoalCompleted, MarkGoalAbandoned, DeleteSavingsGoal |
| `Budget` | CreateBudget, UpdateBudget, ActivateBudget, DeactivateBudget, EnableRollover, DisableRollover, RecordSpendingObservation, RollOverBudgetPeriod, DeleteBudget |

### 6.2 Events per aggregate

| Aggregate | Events |
|-----------|--------|
| `Expense` | ExpenseCreated, ExpenseUpdated, ExpenseDeleted, ExpenseLinkedToSavingsGoal, ExpenseUnlinkedFromSavingsGoal, ContributionAmountAdjusted, ReceiptAttached, ReceiptRemoved, ExpenseTagsChanged |
| `SavingsGoal` | SavingsGoalCreated, SavingsGoalUpdated, ContributionRecorded, ContributionAdjusted, ContributionRemoved, SavingsGoalCompleted, SavingsGoalReopened, SavingsGoalPaused, SavingsGoalResumed, SavingsGoalAbandoned, SavingsGoalDeleted |
| `Budget` | BudgetCreated, BudgetUpdated, BudgetActivated, BudgetDeactivated, BudgetRolloverConfigChanged, BudgetSpendingUpdated, BudgetThresholdReached, BudgetExceeded, BudgetPeriodRolledOver, BudgetDeleted |

### 6.3 Notes & assumptions

1. **Glossary fidelity.** Every property, command, event, enum, and status uses Glossary terms
   verbatim (e.g. `PaymentMethod.UPI`, `Contribution`, `Savings Category`, `BudgetThreshold`,
   `BudgetPeriod`, `GoalStatus`). Prohibited anti-terms (Outflow, Deposit, Cap, etc.) are absent.
2. **Money is always INR.** No primitive monetary fields; all amounts are `Money` (scale-2, INR).
3. **Idempotent alerting.** `Budget.firedThresholds` (BUD-INV-5) and reconciliation via the backing
   Expense (SG-INV-3) are the two correctness anchors flagged for unit-test priority (CQ-5/CQ-7).
4. **Events are facts, not entities.** Payloads carry Ids + changed values only (AL-4); subscribers
   re-query via ports if they need more.
5. **Status modelling.** `Expense` is existence-based (no status enum); `SavingsGoal` and `Budget`
   carry explicit lifecycles. `Budget` "DEACTIVATED/DELETED" are shown as lifecycle nodes driven by
   the `active` flag and deletion rather than a stored enum, to match REQ-BUD-002.
6. **Scope.** Only the three requested aggregates are detailed. `User`, `Category`, and
   `RecurringExpense` lifecycles are deferred; their events referenced here (e.g. via ports) are
   specified in `05-domain-model.md`.

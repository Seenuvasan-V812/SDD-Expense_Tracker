# Event Catalog — Daily Expense Application

| Field | Value |
|-------|-------|
| **Document** | 08 — Event Catalog (Asynchronous Domain Events) |
| **Feature** | Daily Expense Application |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Status** | Draft |
| **Created** | 2026-06-25 |
| **Author Role** | Event-Driven Architecture Expert |
| **Source Inputs** | `06-aggregate-specifications.md`, `05-domain-model.md`, `.specify/memory/constitution.md` (v1.1.2) |
| **Governing Authority** | [Daily Expense Application — Engineering Constitution](../../.specify/memory/constitution.md) |
| **Vocabulary Authority** | [Ubiquitous Language Glossary](./02-glossary.md) |

> **Purpose.** The authoritative catalog of every **asynchronous domain event** crossing a service
> boundary. Events are the *only* sanctioned cross-context communication channel besides synchronous
> Anti-Corruption Ports (AL-1/AL-2): no service reads another's schema. Each entry defines **name,
> producer, consumers, payload JSON schema, and trigger condition**. Names and fields use the
> [Glossary](./02-glossary.md) verbatim.

---

## 1. Event Conventions (binding)

### 1.1 Naming & semantics

- **Event name:** `PascalCase`, **past tense**, suffix `Event` (e.g. `ExpenseCreatedEvent`). An
  event is an immutable **fact that already happened** — never a command or request.
- **Payloads carry Ids + changed values only** — never a serialized JPA entity (AL-4). A consumer
  needing more data re-queries the producer via its Anti-Corruption Port.
- **No PII in payloads or logs.** Email, full name, and raw amounts are excluded from event bodies
  where not strictly required; monetary values that *are* required travel as the structured `Money`
  object and must not be logged (CQ-13).

### 1.2 Standard envelope (every event)

Every event is wrapped in this envelope; the table entries below specify only the `payload`.

```json
{
  "eventId":     "uuid",                 // unique per emission (idempotency key)
  "eventType":   "ExpenseCreatedEvent",  // PascalCase name
  "eventVersion":"1.0",                  // schema version (additive evolution)
  "occurredAt":  "2026-06-25T10:15:30+05:30",  // IST, ISO-8601
  "producer":    "expense-service",      // emitting service
  "userId":      "uuid",                 // owning General User (partition/correlation key)
  "traceId":     "uuid",                 // propagated from the originating request (P7 / CQ-12)
  "payload":     { /* event-specific, see below */ }
}
```

### 1.2.1 Phase 1 Event Disposition (general rule — EVT-005/EVT-006)

Events whose only active consumer is `notification-service` or `reporting-service` (both Phase 2 deferred):

- The outbox relay **publishes them to the designated Kafka topic**.
- The `notification-service` and `reporting-service` consumer groups do **not exist** in Phase 1.
- Messages are **retained** per Kafka topic retention policy (default: 7 days) for Phase 2 consumption.
- Producing services write the event to the outbox in the same transaction as the state change (CQ-8) — the outbox relay is the only Phase 1 consumer.

---

### 1.3 Delivery guarantees

| Concern | Rule |
|---------|------|
| **Ordering** | Per-aggregate ordering via `userId` + aggregate id as partition key. |
| **Delivery** | At-least-once; **all consumers MUST be idempotent** keyed on `eventId`. |
| **Reliability** | Producers publish via the transactional outbox (event written in the same write transaction as the state change — CQ-8 — and relayed after commit). |
| **Failure** | A failed recurring-generation surfaces as `RecurringGenerationFailedEvent` (a first-class event), not a silent drop (REQ-NOTIF-003). |

### 1.4 Producers & consumers (service legend)

| Service | Bounded Context |
|---------|-----------------|
| `user-service` | Identity & Access |
| `category-service` | Category |
| `expense-service` | Expense / Transaction |
| `savings-goal-service` | Savings Goal |
| `budget-service` | Budget |
| `income-service` | Income |
| `notification-service` | Notification |
| `reporting-service` | Reporting & Analytics |

---

## 2. Event Flow Overview

```text
user-service ──UserRegisteredEvent──────────────▶ notification-service (verification email)
             ──UserDeletedEvent──────────────────▶ ALL services (cascade data removal)

expense-service ─ExpenseCreated/Updated/Deleted──▶ budget-service (spending feed)
                                                  └▶ reporting-service (aggregates)
                ─ContributionAmountAdjusted──────▶ savings-goal-service (reconcile total)
                ─ExpenseDeleted (was linked)─────▶ savings-goal-service (decrement)
                ─RecurringGenerationFailed───────▶ notification-service (in-app alert)

savings-goal-service ─SavingsGoalCompleted───────▶ notification-service (in-app "Completed")
                     ─SavingsGoalDeleted──────────▶ expense-service (unlink backing Expenses)
                     ─ContributionRecorded────────▶ reporting-service / dashboard

budget-service ─BudgetThresholdReached(80%)──────▶ notification-service (Budget Alert in-app+email)
               ─BudgetExceeded─────────────────────▶ notification-service (Budget Alert in-app+email)
               ─BudgetSpendingUpdated──────────────▶ reporting-service (dashboard status)

scheduler (internal) ─WeeklyDigestDue──────────────▶ notification-service (digest email, opt-in)
```

---

## 3. Identity & Access Events  *(producer: `user-service`)*

### 3.1 `UserRegisteredEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `user-service` |
| **Consumers** | `notification-service` (send Email Verification) |
| **Trigger** | A `User` is created in `INACTIVE_UNVERIFIED` status after `RegistrationService` succeeds (REQ-USR-003/004). |

```json
{
  "userId": "uuid",
  "deliveryRef": "uuid"  // opaque, non-secret reference — notification-service calls SecureNotificationDeliveryPort
                         // to obtain the one-time verification URL and the user's email address.
                         // The raw verification token never leaves user-service (S-01 / SEC-1).
}
```

### 3.2 `UserVerifiedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `user-service` |
| **Consumers** | `reporting-service` (active-user metric); `notification-service` (optional welcome) |
| **Trigger** | A valid Email Verification link is consumed and `User.status` → `ACTIVE` (REQ-USR-004). |

```json
{ "userId": "uuid", "verifiedAt": "date-time" }
```

> **Phase 1 disposition (EVT-005).** Published to outbox and relayed to Kafka topic `user-events`; no active consumer in Phase 1. Retained per topic retention policy for Phase 2 `reporting-service` backfill.

### 3.3 `PasswordResetRequestedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `user-service` |
| **Consumers** | `notification-service` (send time-limited reset link) |
| **Trigger** | `forgot-password` accepted; a time-limited reset token is issued (REQ-USR-007). Rate-limited (SEC-4). |

```json
{
  "userId": "uuid",
  "deliveryRef": "uuid",    // opaque, non-secret reference — notification-service calls SecureNotificationDeliveryPort
                            // to obtain the one-time reset URL. Raw token never leaves user-service (S-01 / SEC-1).
  "expiresAt": "date-time"  // included so the email body can display the link expiry time (not a secret)
}
```

### 3.4 `UserDeletedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `user-service` |
| **Consumers** | **ALL** owning services (`category`, `expense`, `savings-goal`, `budget`, `income`, `notification`, `reporting`) — cascade removal of that user's data |
| **Trigger** | `User` account deletion; all associated data must be removed (REQ-USR-010). |

```json
{ "userId": "uuid", "deletedAt": "date-time" }
```

### 3.5 `DataExportRequestedEvent` / `DataExportReadyEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `user-service` |
| **Consumers** | All owning services (gather data via `UserDataPort`); `notification-service` (notify when ready) |
| **Trigger** | `POST /users/me/data-export` accepted (REQ-USR-011). `Ready` fires when the single export file is assembled. |

```json
// DataExportRequestedEvent
{ "userId": "uuid", "exportId": "uuid", "requestedAt": "date-time" }
// DataExportReadyEvent — downloadRef intentionally excluded (SENSITIVE, Doc 10 §4.4 / S-02).
// notification-service sends a generic "your export is ready" in-app alert.
// The user fetches a time-limited signed URL via: GET /api/v1/users/me/data-export/{exportId}/download
{ "userId": "uuid", "exportId": "uuid", "readyAt": "date-time" }
```

> **Phase 1 disposition (EVT-004).** `DataExportRequestedEvent` is consumed by each service's in-process `UserDataPort` implementation (each service exports its own data to MinIO synchronously). `DataExportReadyEvent` is published to the outbox; notification delivery is parked until Phase 2. `GET /users/me/data-export/{exportId}/download` returns export status synchronously via polling in Phase 1.

---

## 4. Expense / Transaction Events  *(producer: `expense-service`)*

### 4.1 `ExpenseCreatedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `expense-service` |
| **Consumers** | `budget-service` (spending feed → threshold evaluation); `reporting-service` (aggregates); `savings-goal-service` (only when `savingsGoalId` present) |
| **Trigger** | `CreateExpenseCommand` succeeds — a new `Expense` is persisted (EXP-INV-1..5; REQ-EXP-001). |

```json
{
  "expenseId": "uuid",
  "categoryId": "uuid",
  "amount": { "amount": "450.00", "currency": "INR" },
  "date": "2026-06-20",
  "paymentMethod": "UPI",
  "savingsGoalId": "uuid|null",
  "recurringExpenseId": "uuid|null"
}
```

### 4.2 `ExpenseUpdatedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `expense-service` |
| **Consumers** | `budget-service`, `reporting-service` |
| **Trigger** | `UpdateExpenseDetailsCommand` succeeds and changes affect amount/date/category (REQ-EXP-006). |

```json
{
  "expenseId": "uuid",
  "changed": {
    "amount": { "amount": "500.00", "currency": "INR" } ,   // present only if changed
    "date": "2026-06-21",                                    // present only if changed
    "categoryId": "uuid"                                     // present only if changed
  }
}
```

### 4.3 `ExpenseDeletedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `expense-service` |
| **Consumers** | `budget-service` (reduce spending); `reporting-service`; `savings-goal-service` (decrement, if was linked) |
| **Trigger** | `DeleteExpenseCommand` succeeds (REQ-EXP-008). Carries `savingsGoalId` so a linked goal's total decreases. |

```json
{
  "expenseId": "uuid",
  "categoryId": "uuid",
  "amount": { "amount": "450.00", "currency": "INR" },
  "date": "2026-06-20",
  "savingsGoalId": "uuid|null"
}
```

### 4.4 `ExpenseLinkedToSavingsGoalEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `expense-service` |
| **Consumers** | `savings-goal-service` (add `ContributionEntry`, increase total) |
| **Trigger** | An Expense's `savingsGoalId` is set — via `CreateExpenseCommand` with a goal, or `LinkExpenseToSavingsGoalCommand` (secondary contribution flow; REQ-GOAL-005). Category forced to Savings Category (EXP-INV-5). |

```json
{
  "expenseId": "uuid",
  "savingsGoalId": "uuid",
  "amount": { "amount": "5000.00", "currency": "INR" },
  "date": "2026-06-18",
  "source": "LINKED_EXPENSE"
}
```

### 4.5 `ExpenseUnlinkedFromSavingsGoalEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `expense-service` |
| **Consumers** | `savings-goal-service` (remove `ContributionEntry`, decrease total) |
| **Trigger** | `UnlinkExpenseFromSavingsGoalCommand` succeeds; the Expense remains a regular Expense (REQ-EXP-007). |

```json
{ "expenseId": "uuid", "savingsGoalId": "uuid", "amount": { "amount": "5000.00", "currency": "INR" } }
```

### 4.6 `ContributionAmountAdjustedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `expense-service` |
| **Consumers** | `savings-goal-service` (reconcile Contribution total) |
| **Trigger** | The amount or date of a **Contribution-backing Expense** (one with `savingsGoalId`) changes (EXP-INV-9; REQ-GOAL-007). |

```json
{
  "expenseId": "uuid",
  "savingsGoalId": "uuid",
  "oldAmount": { "amount": "10000.00", "currency": "INR" },
  "newAmount": { "amount": "12000.00", "currency": "INR" },
  "newDate": "2026-06-20"
}
```

### 4.7 `ReceiptAttachedEvent` / `ReceiptRemovedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `expense-service` |
| **Consumers** | `reporting-service` (optional metrics) |
| **Trigger** | `AttachReceiptCommand` (after type/size validation, SEC-5) / `RemoveReceiptCommand` succeeds (REQ-EXP-009/011). |

```json
{ "expenseId": "uuid" }
```

### 4.8 `ExpenseTagsChangedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `expense-service` |
| **Consumers** | `reporting-service` (optional, tag analytics) |
| **Trigger** | `ApplyTagsCommand` / `RemoveTagCommand` changes the Expense's Tag set (REQ-TAG-002). |

```json
{ "expenseId": "uuid", "addedTags": ["office"], "removedTags": ["vacation"] }
```

### 4.9 `RecurringExpenseGeneratedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `expense-service` |
| **Consumers** | `budget-service`, `reporting-service` (treat like a normal Expense creation); **`savings-goal-service`** (conditional — only when `savingsGoalId` is non-null; triggers `RecordContributionCommand` on the referenced `SavingsGoal` — EVT-003) |
| **Trigger** | `RecurringExpenseGenerator` produces the next **Occurrence** on the scheduled date (REQ-REC-003). |

```json
{
  "expenseId": "uuid",
  "recurringExpenseId": "uuid",
  "categoryId": "uuid",
  "amount": { "amount": "999.00", "currency": "INR" },
  "date": "2026-07-01",
  "paymentMethod": "NET_BANKING",
  "savingsGoalId": "uuid|null"
}
```

> **EVT-003 note.** `savingsGoalId` is non-null when the `RecurringExpense` template was linked to a Savings Goal. `savings-goal-service` MUST check this field before processing; if null, the event is ignored by that consumer.

### 4.10 `RecurringGenerationFailedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `expense-service` |
| **Consumers** | `notification-service` (in-app failure Notification) |
| **Trigger** | A scheduled recurring Expense (or Income) Occurrence fails to generate (REQ-NOTIF-003). |

```json
{ "recurringExpenseId": "uuid", "scheduledDate": "2026-07-01", "reason": "code-safe message (no PII)" }
```

---

## 5. Savings Goal Events  *(producer: `savings-goal-service`)*

### 5.1 `SavingsGoalCreatedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `savings-goal-service` |
| **Consumers** | `reporting-service` (dashboard goals section) |
| **Trigger** | `CreateSavingsGoalCommand` succeeds (REQ-GOAL-001). |

```json
{
  "savingsGoalId": "uuid",
  "name": "MacBook Pro",
  "targetAmount": { "amount": "120000.00", "currency": "INR" },
  "targetDate": "2026-12-31"
}
```

### 5.1.1 `SavingsGoalUpdatedEvent`  *(added — EVT-001)*

| Attribute | Value |
|-----------|-------|
| **Producer** | `savings-goal-service` |
| **Consumers** | `reporting-service` (dashboard goals section — Phase 2) |
| **Trigger** | `UpdateSavingsGoalCommand` succeeds (REQ-GOAL-002). |

```json
{
  "savingsGoalId": "uuid",
  "userId": "uuid",
  "changedFields": ["name", "targetAmount", "targetDate"]
}
```

> **Phase 1 disposition.** Published to outbox and relayed to Kafka topic `savings-goal-events`. `reporting-service` consumer group not active in Phase 1; events are retained per topic retention policy.

---

### 5.2 `ContributionRecordedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `savings-goal-service` |
| **Consumers** | `reporting-service` / dashboard |
| **Trigger** | A `ContributionEntry` is appended via `RecordContributionCommand` (primary flow) or `LinkExistingExpenseCommand` (secondary flow); total increases (REQ-GOAL-004/005/006). |

```json
{
  "savingsGoalId": "uuid",
  "expenseId": "uuid",
  "amount": { "amount": "10000.00", "currency": "INR" },
  "date": "2026-06-20",
  "source": "GOAL_SCREEN",
  "totalContributed": { "amount": "10000.00", "currency": "INR" }
}
```

### 5.3 `ContributionAdjustedEvent` / `ContributionRemovedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `savings-goal-service` |
| **Consumers** | `reporting-service` / dashboard |
| **Trigger** | `ReconcileContributionCommand` runs after an Expense edit/delete/unlink event; the goal total is recomputed (REQ-GOAL-007). |

```json
// ContributionAdjustedEvent
{ "savingsGoalId": "uuid", "expenseId": "uuid", "newAmount": {"amount":"12000.00","currency":"INR"}, "totalContributed": {"amount":"12000.00","currency":"INR"} }
// ContributionRemovedEvent
{ "savingsGoalId": "uuid", "expenseId": "uuid", "totalContributed": {"amount":"0.00","currency":"INR"} }
```

### 5.4 `SavingsGoalCompletedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `savings-goal-service` |
| **Consumers** | **`notification-service`** (in-app "goal Completed" Notification); `reporting-service` |
| **Trigger** | `totalContributed ≥ targetAmount` while `ACTIVE` (auto, SG-INV-6) **or** `MarkGoalCompletedCommand` (manual). `completedAutomatically` distinguishes the two (REQ-GOAL-011/012). |

```json
{
  "savingsGoalId": "uuid",
  "totalContributed": { "amount": "120000.00", "currency": "INR" },
  "targetAmount": { "amount": "120000.00", "currency": "INR" },
  "completedAutomatically": true
}
```

### 5.5 `SavingsGoalReopenedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `savings-goal-service` |
| **Consumers** | `reporting-service` / dashboard |
| **Trigger** | Reconciliation drops `totalContributed` below `targetAmount`, reverting `COMPLETED → ACTIVE` (SG-INV-3). |

```json
{ "savingsGoalId": "uuid", "totalContributed": { "amount": "110000.00", "currency": "INR" } }
```

### 5.6 `SavingsGoalPausedEvent` / `SavingsGoalResumedEvent` / `SavingsGoalAbandonedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `savings-goal-service` |
| **Consumers** | `reporting-service` / dashboard |
| **Trigger** | `PauseSavingsGoalCommand` (excludes from projection & active list — REQ-GOAL-013) / `ResumeSavingsGoalCommand` / `MarkGoalAbandonedCommand` (REQ-GOAL-012). |

```json
{ "savingsGoalId": "uuid", "newStatus": "PAUSED" }
```

### 5.7 `SavingsGoalDeletedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `savings-goal-service` |
| **Consumers** | **`expense-service`** (unlink backing Expenses — they survive); `reporting-service` |
| **Trigger** | `DeleteSavingsGoalCommand`; the goal is removed but its backing Expenses must be detached, not deleted (SG-INV-8; REQ-GOAL-003). |

```json
{ "savingsGoalId": "uuid", "detachedExpenseIds": ["uuid", "uuid"] }
```

---

## 6. Budget Events  *(producer: `budget-service`)*

### 6.1 `BudgetSpendingUpdatedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `budget-service` |
| **Consumers** | `reporting-service` (dashboard Budget status) |
| **Trigger** | `RecordSpendingObservationCommand` recomputes `spent` for the current Budget Period from an Expense event (REQ-BUD-007). |

```json
{
  "budgetId": "uuid",
  "periodWindow": { "startDate": "2026-06-01", "endDate": "2026-06-30" },
  "spent": { "amount": "8000.00", "currency": "INR" },
  "percentUsed": 80.0
}
```

### 6.2 `BudgetThresholdReachedEvent`  *(the 80% breach)*

| Attribute | Value |
|-----------|-------|
| **Producer** | `budget-service` |
| **Consumers** | **`notification-service`** (deliver Budget Alert as in-app Notification **and** email) |
| **Trigger** | `spent ≥ 0.8 × effectiveLimit` for an **active** Budget, and `EIGHTY_PERCENT` is **not yet** in `firedThresholds` (fires once per Budget Period — BUD-INV-5/7; REQ-BUD-004/006). |

```json
{
  "budgetId": "uuid",
  "threshold": "EIGHTY_PERCENT",
  "periodWindow": { "startDate": "2026-06-01", "endDate": "2026-06-30" },
  "spent": { "amount": "8000.00", "currency": "INR" },
  "effectiveLimit": { "amount": "10000.00", "currency": "INR" }
}
```

### 6.2.1 `BudgetExceededEvent`  *(the over-budget breach)*

| Attribute | Value |
|-----------|-------|
| **Producer** | `budget-service` |
| **Consumers** | **`notification-service`** (Budget Alert in-app + email) |
| **Trigger** | `spent ≥ effectiveLimit` for an **active** Budget, and `EXCEEDED` is **not yet** in `firedThresholds` (once per Budget Period — BUD-INV-5/6/7; REQ-BUD-005/006). |

```json
{
  "budgetId": "uuid",
  "threshold": "EXCEEDED",
  "periodWindow": { "startDate": "2026-06-01", "endDate": "2026-06-30" },
  "spent": { "amount": "10500.00", "currency": "INR" },
  "effectiveLimit": { "amount": "10000.00", "currency": "INR" }
}
```

> **Glossary mapping.** The user's "`BudgetThresholdBreached`" concept is realised by **two**
> distinct, idempotent events — `BudgetThresholdReachedEvent` (80%) and `BudgetExceededEvent`
> (100%+) — because each fires at most once per period per threshold (REQ-BUD-006). The
> Notification service treats both as **Budget Alert** deliveries.

### 6.3 `BudgetPeriodRolledOverEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `budget-service` |
| **Consumers** | `reporting-service` |
| **Trigger** | `RollOverBudgetPeriodCommand` at a Budget Period boundary; a new ledger opens, `firedThresholds` resets, and unspent amount carries in when `rolloverEnabled` (BUD-INV-8; REQ-BUD-003/006). |

```json
{
  "budgetId": "uuid",
  "newPeriodWindow": { "startDate": "2026-07-01", "endDate": "2026-07-31" },
  "carriedIn": { "amount": "3000.00", "currency": "INR" }
}
```

### 6.3.1 `BudgetRolloverConfigChangedEvent`  *(added — EVT-002)*

| Attribute | Value |
|-----------|-------|
| **Producer** | `budget-service` |
| **Consumers** | `reporting-service` (Phase 2) |
| **Trigger** | `EnableRolloverCommand` or `DisableRolloverCommand` succeeds (REQ-BUD-003). |

```json
{ "budgetId": "uuid", "userId": "uuid", "rolloverEnabled": true }
```

> **Phase 1 disposition.** Published to outbox and relayed to Kafka topic `budget-events`; no active consumer in Phase 1.

---

### 6.4 `BudgetCreatedEvent` / `BudgetUpdatedEvent` / `BudgetActivatedEvent` / `BudgetDeactivatedEvent` / `BudgetDeletedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `budget-service` |
| **Consumers** | `reporting-service` (dashboard) |
| **Trigger** | Respective lifecycle commands; **deactivated Budgets fire no Budget Alerts** (BUD-INV-7; REQ-BUD-002). |

```json
{ "budgetId": "uuid", "scope": "CATEGORY", "categoryId": "uuid|null", "active": true }
```

---

## 7. Income & Scheduler Events  *(supporting producers)*

### 7.1 `IncomeRecordedEvent` / `IncomeUpdatedEvent` / `IncomeDeletedEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | `income-service` |
| **Consumers** | `reporting-service` (Net Savings, Savings Rate, income-vs-expense charts) |
| **Trigger** | Income entry created/edited/deleted, including recurring Income Occurrences (REQ-INC-001..004). |

```json
{ "incomeId": "uuid", "categoryId": "uuid", "amount": { "amount": "50000.00", "currency": "INR" }, "date": "2026-06-01", "source": "Salary" }
```

### 7.2 `WeeklyDigestDueEvent`

| Attribute | Value |
|-----------|-------|
| **Producer** | internal **Time-Based Scheduler** |
| **Consumers** | `notification-service` (compose & send the Weekly Digest email — opt-in, default off) |
| **Trigger** | Every Monday for users who opted in (REQ-NOTIF-001). |

```json
{ "userId": "uuid", "weekStart": "2026-06-15", "weekEnd": "2026-06-21" }
```

---

## 8. Master Event Index

| # | Event | Producer | Primary Consumers | Trigger (rule) |
|---|-------|----------|-------------------|----------------|
| 1 | `UserRegisteredEvent` | user | notification | User created, inactive (REQ-USR-004) |
| 2 | `UserVerifiedEvent` | user | reporting, notification | Email Verification consumed |
| 3 | `PasswordResetRequestedEvent` | user | notification | Reset link requested (REQ-USR-007) |
| 4 | `UserDeletedEvent` | user | **all** | Account deleted (REQ-USR-010) |
| 5 | `DataExportRequestedEvent` | user | all, notification | Export requested (REQ-USR-011) |
| 6 | `DataExportReadyEvent` | user | notification | Export file assembled |
| 7 | `ExpenseCreatedEvent` | expense | budget, reporting, savings-goal* | Expense created (REQ-EXP-001) |
| 8 | `ExpenseUpdatedEvent` | expense | budget, reporting | Amount/date/category changed |
| 9 | `ExpenseDeletedEvent` | expense | budget, reporting, savings-goal* | Expense deleted (REQ-EXP-008) |
| 10 | `ExpenseLinkedToSavingsGoalEvent` | expense | savings-goal | `savingsGoalId` set (REQ-GOAL-005) |
| 11 | `ExpenseUnlinkedFromSavingsGoalEvent` | expense | savings-goal | Goal association removed |
| 12 | `ContributionAmountAdjustedEvent` | expense | savings-goal | Backing Expense amount/date changed (REQ-GOAL-007) |
| 13 | `ReceiptAttachedEvent` | expense | reporting | Receipt uploaded (REQ-EXP-009) |
| 14 | `ReceiptRemovedEvent` | expense | reporting | Receipt deleted (REQ-EXP-011) |
| 15 | `ExpenseTagsChangedEvent` | expense | reporting | Tags changed (REQ-TAG-002) |
| 16 | `RecurringExpenseGeneratedEvent` | expense | budget, reporting | Next Occurrence generated (REQ-REC-003) |
| 17 | `RecurringGenerationFailedEvent` | expense | notification | Generation failed (REQ-NOTIF-003) |
| 18 | `SavingsGoalCreatedEvent` | savings-goal | reporting | Goal created (REQ-GOAL-001) |
| 19 | `ContributionRecordedEvent` | savings-goal | reporting | Contribution appended (REQ-GOAL-004/005) |
| 20 | `ContributionAdjustedEvent` | savings-goal | reporting | Reconcile up/down (REQ-GOAL-007) |
| 21 | `ContributionRemovedEvent` | savings-goal | reporting | Contribution removed (REQ-GOAL-007) |
| 22 | `SavingsGoalCompletedEvent` | savings-goal | **notification**, reporting | Total ≥ target, or manual (REQ-GOAL-011/012) |
| 23 | `SavingsGoalReopenedEvent` | savings-goal | reporting | Total dropped below target |
| 24 | `SavingsGoalPausedEvent` | savings-goal | reporting | Goal paused (REQ-GOAL-013) |
| 25 | `SavingsGoalResumedEvent` | savings-goal | reporting | Goal resumed |
| 26 | `SavingsGoalAbandonedEvent` | savings-goal | reporting | Goal abandoned (REQ-GOAL-012) |
| 27 | `SavingsGoalDeletedEvent` | savings-goal | **expense**, reporting | Goal deleted; detach Expenses (REQ-GOAL-003) |
| 28 | `BudgetSpendingUpdatedEvent` | budget | reporting | Spending recomputed (REQ-BUD-007) |
| 29 | `BudgetThresholdReachedEvent` | budget | **notification** | ≥80%, once/period (REQ-BUD-004/006) |
| 30 | `BudgetExceededEvent` | budget | **notification** | ≥100%, once/period (REQ-BUD-005/006) |
| 31 | `BudgetPeriodRolledOverEvent` | budget | reporting | Period boundary; rollover (REQ-BUD-003) |
| 32 | `BudgetCreatedEvent` | budget | reporting | Budget created (REQ-BUD-001) |
| 33 | `BudgetUpdatedEvent` | budget | reporting | Budget edited |
| 34 | `BudgetActivatedEvent` | budget | reporting | Activated (REQ-BUD-002) |
| 35 | `BudgetDeactivatedEvent` | budget | reporting | Deactivated; no alerts (REQ-BUD-002) |
| 36 | `BudgetDeletedEvent` | budget | reporting | Budget deleted |
| 37 | `IncomeRecordedEvent` | income | reporting | Income created (REQ-INC-001) |
| 38 | `IncomeUpdatedEvent` | income | reporting | Income edited |
| 39 | `IncomeDeletedEvent` | income | reporting | Income deleted |
| 40 | `WeeklyDigestDueEvent` | scheduler | notification | Monday, opt-in (REQ-NOTIF-001) |
| 41 | `SavingsGoalUpdatedEvent` | savings-goal | reporting | Goal fields updated (REQ-GOAL-002) — *EVT-001* |
| 42 | `BudgetRolloverConfigChangedEvent` | budget | reporting | Rollover enabled/disabled (REQ-BUD-003) — *EVT-002* |
| 43 | `OccurrenceEditedEvent` | expense | reporting | Single occurrence edited (scope=THIS / CMD-001) |
| 44 | `FutureOccurrencesTruncatedEvent` | expense | reporting, notification* | scope=THIS_AND_FUTURE — template truncated (CMD-001) |
| 45 | `OccurrenceDeletedEvent` | expense | savings-goal*, reporting | Occurrence deleted; scope THIS or THIS_AND_FUTURE (CMD-001) |

`*` = consumed by `savings-goal-service` only when the Expense carries a `savingsGoalId`.

---

## 9. Notes & Assumptions

1. **Two breach events, not one.** The requested `BudgetThresholdBreached` is split into
   `BudgetThresholdReachedEvent` (80%) and `BudgetExceededEvent` (100%+), each idempotent and
   once-per-period (REQ-BUD-006) — the single most regression-prone rule in the system.
2. **Notification is a pure consumer.** It produces no domain events in this catalog; it *consumes*
   `UserRegisteredEvent`, `PasswordResetRequestedEvent`, `RecurringGenerationFailedEvent`,
   `SavingsGoalCompletedEvent`, `BudgetThresholdReachedEvent`, `BudgetExceededEvent`, and
   `WeeklyDigestDueEvent`, then dispatches in-app Notifications and emails.
3. **Reporting is read-only.** It subscribes broadly to build Dashboard/Report aggregates and emits
   nothing.
4. **Idempotency is mandatory.** At-least-once delivery + the `eventId` key + the producer-side
   transactional outbox (CQ-8) guarantee no double-counting (e.g. a redelivered Expense event must
   not double a budget's `spent` or a goal's total).
5. **No PII in payloads.** Email/name never travel in event bodies; consumers needing them resolve
   via the owning service's port (CQ-13 / SEC-3).
6. **Money is structured** (`{amount, currency:"INR"}`) everywhere monetary values appear, matching
   the `Money` Value Object.
7. **Scope.** Events for the five core contexts are fully specified; `income-service`,
   `notification-service`, and `reporting-service` appear as producers/consumers to complete the
   flow, with Income/Scheduler events catalogued in §7. Internal (non-cross-boundary) events are
   omitted by design — this catalog lists only events that cross a service boundary.
8. **Secrets removed from event payloads (S-01 / S-02 — validation report).** `UserRegisteredEvent`
   and `PasswordResetRequestedEvent` previously contained raw `verificationToken`/`resetToken` values.
   These are **SECRET** class (Doc 10 §4.1) and must never leave `user-service`. Both events now
   carry only an opaque `deliveryRef`; `notification-service` resolves the actual one-time URL via
   `SecureNotificationDeliveryPort` (Doc 05 §8) at email-dispatch time. `DataExportReadyEvent`
   previously contained a raw `downloadRef` (SENSITIVE, Doc 10 §4.4); it is now omitted — users
   fetch a signed URL via `GET /api/v1/users/me/data-export/{exportId}/download` instead.

# Contract — `budget-service` (Budget)

**Base**: `/api/v1` | **Derived from**: [`07-api-specification.md`](../07-api-specification.md) §6 + Doc 06 §4.
Global rules (auth, pagination/error envelopes, status codes incl. 403-never-404, security headers) per
[`user-service.md`](./user-service.md) "Global rules".

## Endpoints (7)

| Method | Path | Purpose | Success | Key failures | Req |
|--------|------|---------|---------|--------------|-----|
| GET | `/budgets` | List own Budgets with current status | 200 (page) | 401 | REQ-BUD-007 |
| GET | `/budgets/{id}` | Status: set / spent / remaining / % used | 200 | 401, 403, 404 | REQ-BUD-007 |
| POST | `/budgets` | Create (CATEGORY or OVERALL) | 201 + `Location` | 400 (limit ≤ 0), 403 (foreign category) | REQ-BUD-001 |
| PUT | `/budgets/{id}` | Edit limit / period / scope | 200 | 400, 403, 404 | REQ-BUD-001 |
| PATCH | `/budgets/{id}/activation` | Activate / deactivate (no delete) | 200 | 400, 403, 404 | REQ-BUD-002 |
| PATCH | `/budgets/{id}/rollover` | Enable / disable Rollover | 200 | 400, 403, 404 | REQ-BUD-003 |
| DELETE | `/budgets/{id}` | Delete | 204 | 403, 404 | REQ-BUD-001 |

> Budget Threshold breach alerts (80% / exceeded) are **not** a client endpoint — they are produced
> internally by `BudgetEvaluationService` from spending events and (Phase 2) delivered via Notification.
> Clients read breach state through `GET /budgets/{id}` (`firedThresholds`).

## DTOs
- `CreateBudgetRequest{scope∈{OVERALL,CATEGORY}, categoryId?(required iff CATEGORY), limit:Money(>0), period∈{WEEKLY,MONTHLY}, rolloverEnabled}`.
- `UpdateBudgetRequest{limit?(>0), period?, scope?, categoryId?(required when scope→CATEGORY)}`.
- `BudgetStatusResponse{budgetId, scope, categoryId?, limit, period, active, rolloverEnabled, periodWindow{startDate,endDate}, carriedIn, spent, remaining(=effectiveLimit−spent), percentUsed, firedThresholds[∈{EIGHTY_PERCENT,EXCEEDED}]}`.
- `ActivationRequest{active}`; `RolloverRequest{rolloverEnabled}`.

## Key invariants enforced (Doc 06 §4.2)
- `limit > 0` (BUD-INV-1); CATEGORY scope requires a visible `categoryId` (BUD-INV-3, validated via `CategoryLookupPort`).
- `spent` derived **solely** from `SpendingFeedPort` events — never reads Expense schema (BUD-INV-4).
- Each threshold fires **at most once per Budget Period** via persisted `fired_eighty_percent`/`fired_exceeded` booleans (BUD-INV-5) — highest-regression-risk rule.
- Deactivated Budget fires no alerts (BUD-INV-7); rollover seeds next ledger `carriedIn = max(0, prior effectiveLimit − prior spent)` only when enabled, `fired_*` reset (BUD-INV-8).
- Foreign access → 403-never-404.

## Ports / events
Consumes the Expense event stream via `SpendingFeedPort` (`ExpenseCreated/Updated/Deleted`,
`RecurringExpenseGenerated`) → idempotent `RecordSpendingObservationCommand` (dedup on `eventId`), plus
`UserDeletedEvent`. Emits `BudgetCreated/Updated/Activated/Deactivated/Deleted`, `BudgetRolloverConfigChanged`,
`BudgetSpendingUpdated`, `BudgetThresholdReached(80%)` / `BudgetExceeded` (→ Notification, Phase 2),
`BudgetPeriodRolledOver` to the outbox.

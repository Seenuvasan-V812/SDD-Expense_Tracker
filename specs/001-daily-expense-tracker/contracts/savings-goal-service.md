# Contract — `savings-goal-service` (Savings Goal)

**Base**: `/api/v1` | **Derived from**: [`07-api-specification.md`](../07-api-specification.md) §5 + Doc 06 §3.
Global rules (auth, pagination/error envelopes, status codes incl. 403-never-404, security headers) per
[`user-service.md`](./user-service.md) "Global rules".

## Endpoints (8)

| Method | Path | Purpose | Success | Key failures | Req |
|--------|------|---------|---------|--------------|-----|
| GET | `/savings-goals?status=ACTIVE\|COMPLETED\|…` | List (active/completed shown separately) | 200 (page) | 401 | REQ-GOAL-010 |
| GET | `/savings-goals/{id}` | Detail: progress, projection, history | 200 | 401, 403, 404 | REQ-GOAL-008/009 |
| POST | `/savings-goals` | Create | 201 + `Location` | 400 | REQ-GOAL-001 |
| PUT | `/savings-goals/{id}` | Edit | 200 | 400, 403, 404 | REQ-GOAL-002 |
| DELETE | `/savings-goals/{id}` | Delete (detaches, does not delete, Expenses) | 204 | 403, 404 | REQ-GOAL-003 |
| POST | `/savings-goals/{id}/contributions` | Record Contribution from goal screen (primary flow) | 201 + `Location` | 400, 403, 404 | REQ-GOAL-004 |
| GET | `/savings-goals/{id}/contributions` | Contribution History | 200 (page) | 403, 404 | REQ-GOAL-006/008 |
| PATCH | `/savings-goals/{id}/status` | Set PAUSED/COMPLETED/ABANDONED (ACTIVE only from PAUSED) | 200 | 400, 403, 404, 409 illegal transition | REQ-GOAL-012/013 |

> **Secondary flow** (link existing Expense) uses `PUT /expenses/{id}` with `savingsGoalId` (Expense API);
> goal total reconciles via domain events (REQ-GOAL-005/007). No separate endpoint.

## DTOs
- `CreateSavingsGoalRequest{name, targetAmount:Money(>0), targetDate?, description?, icon?, color?}`.
- `UpdateSavingsGoalRequest{name?, targetAmount?(>0; reducing below total auto-COMPLETED), targetDate?(null removes), description?, icon?, color?}`.
- `RecordContributionRequest{amount:Money(>0), date}` — backing Expense under Savings Category created automatically by the service.
- `UpdateGoalStatusRequest{status∈{ACTIVE,PAUSED,COMPLETED,ABANDONED}}` — allowed transitions: ACTIVE→{PAUSED,COMPLETED,ABANDONED}; PAUSED→{ACTIVE,ABANDONED}; COMPLETED→(no manual; reverts to ACTIVE only via internal reconciliation); ABANDONED→terminal. Others → 409.
- `SavingsGoalResponse{savingsGoalId, name, targetAmount, targetDate?, status, totalContributed, remainingAmount(=max(0,target−total)), percentAchieved(0..100), projectedCompletionDate?(null when PAUSED), icon?, color?}`.
- `ContributionEntryResponse{contributionEntryId, expenseId (backing source of truth), amount, date, source∈{GOAL_SCREEN,LINKED_EXPENSE}}`.

## Key invariants enforced (Doc 06 §3.2)
- One `ContributionEntry` per backing Expense (`uq_contribution_entries_goal_expense` — SG-INV-4).
- `totalContributed` = Σ entries, kept consistent with backing Expenses (SG-INV-3).
- Auto-complete (once) when `total ≥ target` while ACTIVE → `SavingsGoalCompletedEvent` (SG-INV-6).
- Delete detaches backing Expenses, does not delete them (SG-INV-8); foreign access → 403-never-404.

## Ports / events
Consumes `ContributionPort` (calls expense-service to create backing Expense) and the Expense event stream
(`ExpenseLinkedToSavingsGoal`, `ExpenseUnlinked`, `ContributionAmountAdjusted`, `ExpenseDeleted` → reconcile,
idempotent on `eventId`), plus `UserDeletedEvent`. Emits `SavingsGoalCreated/Updated`, `ContributionRecorded/Adjusted/Removed`,
`SavingsGoalCompleted` (→ Notification, Phase 2), `SavingsGoalReopened/Paused/Resumed/Abandoned`,
`SavingsGoalDeleted{detachedExpenseIds[]}` (→ expense-service unlinks) to the outbox.

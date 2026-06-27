# Contract — `expense-service` (Expense / Transaction)

**Base**: `/api/v1` | **Derived from**: [`07-api-specification.md`](../07-api-specification.md) §4 + Doc 10 §5.
Global rules (auth, pagination/error envelopes, status codes incl. 403-never-404, security headers) per
[`user-service.md`](./user-service.md) "Global rules". **18 endpoints total** (10 expense + 4 recurring + 4 tags).

## 4.1 Expense endpoints (10)

| Method | Path | Purpose | Success | Key failures | Req |
|--------|------|---------|---------|--------------|-----|
| GET | `/expenses` | List own (paginated/filterable/sortable) | 200 (page) | 401 | REQ-EXP-003/004/005 |
| GET | `/expenses/{id}` | Get one | 200 | 401, 403, 404 | REQ-EXP-003 |
| POST | `/expenses` | Create | 201 + `Location` | 400, 403 (foreign category) | REQ-EXP-001/002 |
| PUT | `/expenses/{id}` | Edit any field incl. goal link (if `savingsGoalId` set, `categoryId` forced to Savings Category; body `categoryId` ignored) | 200 | 400, 403, 404 | REQ-EXP-006/007 |
| DELETE | `/expenses/{id}` | Delete | 204 | 403, 404 | REQ-EXP-008 |
| POST | `/expenses/{id}/receipt` | Upload/replace Receipt (multipart) | 201 + `Location` | 400 (type/size), 403, 404 | REQ-EXP-009, SEC-5 |
| GET | `/expenses/{id}/receipt` | View/download Receipt (binary) | 200 | 403, 404 | REQ-EXP-010 |
| DELETE | `/expenses/{id}/receipt` | Delete Receipt (Expense retained) | 204 | 403, 404 | REQ-EXP-011 |
| POST | `/expenses/import` | Bulk CSV import (`text/csv`, ≤10 MB, ≤10 000 rows) | 200 (report) | 400 (size/type/row-count) | REQ-EXP-012/013 |
| GET | `/expenses/export?from=&to=` | Export date range as CSV (streamed) | 200 (CSV) | 400, 401 | REQ-EXP-014 |

**List filters**: `from`, `to`, `categoryId`, `paymentMethod`, `tag`, `savingsGoalId`; **sort**: `sort=date,desc` / `sort=amount,asc`.

## 4.2 Recurring Expense endpoints (4)
`{id}` in PUT/DELETE is the **`ExpenseId` of a generated occurrence** (not the template); 400 if the Expense has no `recurringExpenseId`.

| Method | Path | Purpose | Success | Failures | Req |
|--------|------|---------|---------|----------|-----|
| GET | `/recurring-expenses` | List templates & schedules | 200 (page) | 401 | REQ-REC-006 |
| POST | `/recurring-expenses` | Create template | 201 + `Location` | 400 | REQ-REC-001/002 |
| PUT | `/recurring-expenses/{id}?scope=THIS\|THIS_AND_FUTURE` | Edit this / this+future occurrence | 200 | 400, 403, 404 | REQ-REC-004 |
| DELETE | `/recurring-expenses/{id}?scope=THIS\|THIS_AND_FUTURE` | Delete this / this+future occurrence | 204 | 403, 404 | REQ-REC-005 |

`THIS_AND_FUTURE` sets template `end_date = occurrence.date − 1 day` and (for edit) creates a new template forward (REC-INV-2).

## 4.3 Tag endpoints (4)

| Method | Path | Purpose | Success | Failures | Req |
|--------|------|---------|---------|----------|-----|
| GET | `/tags` | List own Tags | 200 (page) | 401 | REQ-TAG-001 |
| POST | `/tags` | Create | 201 + `Location` | 400, 409 | REQ-TAG-001 |
| PUT | `/tags/{id}` | Rename | 200 | 400, 403, 404 | REQ-TAG-001 |
| DELETE | `/tags/{id}` | Delete (detaches from Expenses, does not delete them) | 204 | 403, 404 | REQ-TAG-003 |

## DTOs
- `CreateExpenseRequest{amount:Money(>0), date, categoryId (EXPENSE/BOTH, visible), paymentMethod∈{UPI,CASH,CREDIT_CARD,DEBIT_CARD,NET_BANKING,OTHER}, description?, merchant?, notes?, tags?, savingsGoalId?}`.
- `ExpenseResponse{expenseId, amount, date, categoryId, paymentMethod, description?, merchant?, notes?, tags[], hasReceipt, savingsGoalId?, recurringExpenseId?, createdAt, updatedAt}`.
- `CreateRecurringExpenseRequest{prototype:CreateExpenseRequest, frequency∈{DAILY,WEEKLY,MONTHLY,YEARLY}, anchorDate, endDate?, maxOccurrences?}`.
- `ImportExpensesReport{totalRows, succeeded, failed, results[{row, status∈{SUCCEEDED,FAILED,SUCCEEDED_WITH_WARNING}, reason?, warning?}]}`.
- **Money** serialized `{amount:"450.00", currency:"INR"}` (string decimal scale-2).

## Receipt upload security (SEC-5 / Doc 10 §5.2-5.3)
- Multipart `file`; JPEG/PNG/WEBP only, **≤ 5 MB**; type via **magic-byte sniff** (client `Content-Type` not trusted; mismatch → 400).
- Decoded pixels **≤ 25 MP** (pixel-flood/decompression-bomb guard → 400 if exceeded).
- **EXIF stripped server-side** before MinIO write (release blocker if omitted); re-encode to canonical form.
- Storage key = server-generated UUID path `receipts/{userId}/{uuid}` — never client filename.
- One receipt per Expense (`uq_receipts_expense_id`); upload replaces. Binary in MinIO; DB holds `storage_ref`/`mime_type`/`size_bytes`.
- Download serves correct `Content-Type` + `Content-Disposition` + `X-Content-Type-Options: nosniff`, ownership-checked.

## CSV import/export security (Doc 10 §5.5)
- Import: `text/csv` server-validated, ≤ 10 MB, ≤ 10 000 rows; strip leading formula chars (`= + - @ \t \r`) from every cell;
  caller `userId` applied to all rows (CSV `user_id` ignored); goal match against caller's own goals only
  (match→linked Contribution `SUCCEEDED`; no match→plain Expense `SUCCEEDED_WITH_WARNING`). Honors `Idempotency-Key`.
- Export: streamed, never full in-memory load (CQ-10); cell values sanitized against formula injection.

## Events emitted to outbox
`ExpenseCreated/Updated/Deleted`, `ExpenseLinkedToSavingsGoal`, `ExpenseUnlinkedFromSavingsGoal`,
`ContributionAmountAdjusted`, `ReceiptAttached/Removed`, `ExpenseTagsChanged`, `RecurringExpenseGenerated`,
`RecurringGenerationFailed` (→ Notification, Phase 2), `OccurrenceEdited`, `FutureOccurrencesTruncated`, `OccurrenceDeleted`.
Consumes `SavingsGoalDeletedEvent` (unlink backing Expenses) and `UserDeletedEvent` (cascade).

## Ports
Provides `SpendingFeedPort` / `ContributionEventsPort` (via events) and `ContributionPort` HTTP endpoint (savings-goal creates backing Expense). Consumes `CategoryLookupPort` (validate category id/visibility/type).

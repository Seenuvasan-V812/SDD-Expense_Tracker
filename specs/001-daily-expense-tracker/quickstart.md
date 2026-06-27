# Quickstart — End-to-End Validation (Phase 1)

**Purpose**: Runnable validation scenarios proving the Phase-1 feature works end-to-end. Scenarios are
distilled from [`04-feature-specifications.md`](./04-feature-specifications.md) (BDD/Gherkin) and map to
the contracts in [`contracts/`](./contracts/) and the data model in [`data-model.md`](./data-model.md).
Implementation detail (entities, migrations, full test suites) lives in `13-task-breakdown.md` and the
implementation phase, **not** here.

## Prerequisites

- Java 21, Node 20+, Docker + Docker Compose.
- Secrets provided via env (SEC-6): `JWT_SECRET`, `DB_PASSWORD` (per service), MinIO + SMTP creds. **Never hardcoded.**

## Setup

```bash
# From repo root (daily-expense-app/)
docker compose up -d            # postgres×5, minio, kafka, zookeeper, mailhog
mvn -q clean verify             # build + tsc + lint + unit + Testcontainers + OpenAPI contract-diff
# Frontend
cd frontend && npm ci && npm run build && npm run test   # tsc strict (0 any), Vitest/RTL/MSW
```

**Expected**: all infra healthy; CI pipeline green end-to-end; OpenAPI contract-diff clean for all 5 services.

## Run (local dev)

```bash
# Each service boots on its own port against its own DB; identity from JWT only (stateless, AL-5)
mvn -pl services/user-service spring-boot:run        # …repeat per service, or via compose profiles
cd frontend && npm run dev                            # Vite SPA; API base URLs from env (FE-6)
```

Health: `GET /actuator/health` per service returns `UP` (CQ-14). Every response carries the mandatory
security headers (S-08) and every request log line carries a `traceId` (CQ-11/12).

---

## Validation Scenarios

Each scenario states the externally observable outcome. Status codes follow API-4; ownership violations are
**403, never 404** (SEC-3). See [`contracts/`](./contracts/) for exact shapes.

### S1 — Identity & Access (`user-service`) · Doc 04 §2, §12
1. **Register → verify → login** (REQ-USR-003/004/005, SEC-2): register `asha@example.in` → `201`, account
   `INACTIVE_UNVERIFIED`, verification email in MailHog; login before verify → `401`; open verify link →
   active; login → `200` with 15-min access + 7-day refresh.
2. **Password never in plaintext** (SEC-1): stored `password_hash` is BCrypt cost ≥ 12; plaintext absent from
   DB, response, and logs.
3. **Refresh rotation + reuse detection** (SEC-2): refresh → new pair, old refresh → `401` on reuse; reusing a
   revoked token revokes the entire Token Family.
4. **Rate limiting** (SEC-4): repeated failed logins → `429` + `Retry-After`.
5. **Ownership** (SEC-3): Asha requesting Ravi's resource → `403` (not `404`); unauthenticated protected call → `401`.
6. **Account mgmt**: profile update (name/timezone/`en-IN`) → `200`; password change → `204` + all refresh
   tokens revoked; delete account → `204`, all data removed, deleted creds → `401`; data export → `202` then
   download via time-limited signed URL.

### S2 — Category (`category-service`) · Doc 04 §7
1. **Defaults available** (REQ-CAT-001): list includes Savings + Food/Transport/Housing/Health; deleting a
   Default → `409`.
2. **Custom CRUD** (REQ-CAT-002/003): create "Gym" → `201` + `Location`; rename → `200`; delete unused → `204`.
3. **Delete-in-use blocked** (REQ-CAT-005): delete a Custom Category linked to Expenses → `409` ("reassign first").
4. **Type constraint** (REQ-CAT-004): create Expense with an `INCOME`-typed Category → `400`.
5. **Ownership** (SEC-3): delete another user's Custom Category → `403` (not `404`).

### S3 — Expense Creation & Receipts (`expense-service`) · Doc 04 §3
1. **Create required fields** (REQ-EXP-001): amount 450.00, date, Category "Food", `UPI` → `201` + `Location`.
2. **All payment methods** (REQ-USR-002): each of UPI/CASH/CREDIT_CARD/DEBIT_CARD/NET_BANKING/OTHER → `201`.
3. **Validation** (REQ-API-007): missing amount/date/category/method or amount ≤ 0 → `400` with offending field.
4. **Foreign category** (SEC-3): create Expense under another user's Category → `403` (not `404`).
5. **Receipt upload** (SEC-5): PNG 2 MB → stored in MinIO, EXIF stripped, `201`; exactly 5 MB JPEG → accepted;
   PDF/GIF or 6 MB → `400`, not stored; magic-byte mismatch → `400`; download serves `nosniff` +
   `Content-Disposition`; upload to another user's Expense → `403`.

### S4 — Expense List/Edit/Delete & CSV (`expense-service`) · Doc 04 §8
1. **List** (REQ-EXP-003): returns pagination envelope (`content,page,size,totalElements,totalPages`).
2. **Filters/sort** (REQ-EXP-004/005): by date range / Category / Payment Method; sort date desc, amount asc.
3. **Edit/delete** (REQ-EXP-006/008): edit amount → `200`; delete standalone → `204`.
4. **CSV import** (REQ-EXP-012/013): valid 3-row file → `200`, report 3 succeeded; matching goal name → linked
   Contribution; non-matching → `SUCCEEDED_WITH_WARNING`; file > 10 MB → `400`.
5. **CSV export** (REQ-EXP-014): `200`, `Content-Type: text/csv`, only rows in the requested date range; streamed.

### S5 — Recurring & Tags (`expense-service`) · Doc 04 §9, §10
1. **Recurring create/generate** (REQ-REC-001/002/003): MONTHLY template → `201`; scheduler at anchor date
   creates Occurrence with `recurringExpenseId`, `next_run_date` advances.
2. **Occurrence edit/delete scope** (REQ-REC-004/005): `THIS` edits only that Occurrence; `THIS_AND_FUTURE`
   sets template `end_date` to day-before and creates a forward template; non-occurrence ExpenseId → `400`.
3. **Tags** (REQ-TAG-001/002/003): create/rename → `201`/`200`; duplicate name → `409`; delete detaches from
   Expenses without deleting them.

### S6 — Savings Goal Contributions & Lifecycle (`savings-goal-service`) · Doc 04 §4, §11
1. **Primary flow** (REQ-GOAL-004/006): record 10000.00 from goal screen → backing Expense auto-created under
   Savings Category, total = 10000.00, appears in Contribution History and Expense list, `201`.
2. **Reconcile on backing-Expense edit/delete** (REQ-GOAL-007): editing backing Expense to 12000.00 updates
   total; deleting it → total 0.00 (via events, idempotent).
3. **Secondary flow** (REQ-GOAL-005): link existing 5000.00 Expense → total +5000.00; removing link decreases total.
4. **Auto-complete once** (REQ-GOAL-011): contribution crossing target → status `COMPLETED` + in-app completion
   event (Phase 2 delivery); fires exactly once.
5. **Lifecycle** (REQ-GOAL-012/013): PAUSE excludes from projection/active list, preserves history; ABANDON
   terminal; reopening COMPLETED via status API → `409`.
6. **Delete detaches** (REQ-GOAL-003): deleting goal detaches but does not delete backing Expenses.
7. **Ownership** (SEC-3): link Expense to / view another user's goal → `403` (not `404`).

### S7 — Budget Threshold Breaches (`budget-service`) · Doc 04 §5, §13
1. **Create** (REQ-BUD-001): Category Budget "Food" 10000.00 MONTHLY → `201` (spent 0, remaining 10000);
   Overall Weekly Budget → `201`; zero limit → `400`.
2. **80% threshold** (REQ-BUD-004): spending reaching 8000.00 fires one 80% Budget Alert event (in-app + email
   in Phase 2).
3. **Exceeded threshold** (REQ-BUD-005): spending reaching 10500.00 fires one exceeded alert.
4. **Once per period per threshold** (REQ-BUD-006 — highest-risk): repeated spending events in the same period
   produce no duplicate alert for an already-fired threshold; the two thresholds fire independently.
5. **Counter reset on new period** (REQ-BUD-006): a new Budget Period re-arms the 80% alert.
6. **Deactivated** (REQ-BUD-002): deactivated Budget fires no alerts; retained for reactivation.
7. **Rollover** (REQ-BUD-003): with rollover enabled, 3000.00 unspent carries into next period's effective limit.
8. **Status** (REQ-BUD-007): `GET /budgets/{id}` returns set/spent/remaining/percentUsed; another user's
   Budget → `403` (not `404`).

### S8 — Cross-service event flow (real Kafka) · Doc 08, Doc 12 Phase 4
1. **Atomic outbox**: a state change and its outbox row commit together; a failed transaction leaks no event.
2. **Expense → Budget + Goal**: creating an Expense updates the matching Budget ledger `spent` and (if linked)
   the Goal total; duplicate event delivery has a single effect (idempotent on `eventId`).
3. **Isolation** (AL-1): ArchUnit confirms no synchronous cross-service DB access anywhere.

### S9 — Frontend (React SPA) · Doc 12 Phase 5
1. **Strict TS**: `tsc --noEmit` clean, 0 `any`; lint clean.
2. **Single Axios + transparent refresh** (FE-1/FE-2): MSW test — a `401` triggers exactly one refresh
   (single-flight mutex) then replays the original request; no raw `fetch`, no hardcoded base URL.
3. **Explicit states** (FE-4): every data view renders Loading/Error/Empty; never renders undefined data.
4. **a11y + responsive** (REQ-A11Y/RWD): keyboard nav, screen-reader labels, contrast, text resize;
   desktop/tablet/mobile breakpoints pass.

---

## Phase-1 scope guard (must hold)
No scenario exercises Income, Dashboard/Reports, or a Notification **consumer**/Center — these are Phase 2
(O-01..O-04, O-07). Budget/goal/recurring-failure alerts appear here only as **events written to the outbox**
and relayed to Kafka; **no Phase-1 consumer** delivers them. Final release gate additionally confirms
Income/Reporting/Notification are **not** implemented.

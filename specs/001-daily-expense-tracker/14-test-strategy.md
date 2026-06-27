# 14 — Test Strategy — Daily Expense Application

| Field | Value |
|-------|-------|
| **Document** | 14 — Production-Grade Test Strategy |
| **Feature** | Daily Expense Application |
| **Feature Directory** | `specs/001-daily-expense-tracker/` |
| **Status** | Approved for execution |
| **Authored** | 2026-06-27 |
| **Governing Authority** | [Engineering Constitution v1.1.2](../../.specify/memory/constitution.md) |
| **Requirement Authority** | [03-requirement-catalogue.md](./03-requirement-catalogue.md) — 128 REQ-* IDs |
| **API Authority** | [07-api-specification.md](./07-api-specification.md) — 51 endpoint groups |
| **Security Authority** | [10-security-specification.md](./10-security-specification.md) |
| **Vocabulary Authority** | [02-glossary.md](./02-glossary.md) — exact terms throughout |

---

## 1. Test Objectives

| Objective | Measure of Success |
|-----------|-------------------|
| Confirm every REQ-* in the Phase-1 scope is verifiably satisfied | 100% REQ-* coverage in traceability matrix |
| Ensure no Constitution law (AL, SEC, CQ, API, FE) is violated | Zero hard-stop violations in CI |
| Detect cross-service boundary breaches (cross-schema SQL, missing port) | Architecture fitness tests green |
| Verify security controls cannot be bypassed | Penetration scenarios in automated suite |
| Validate data integrity under concurrency and failure injection | Testcontainers chaos scenarios pass |
| Confirm no PII leaks in logs, responses, or error messages | Automated log-scrub assertions |
| Ensure frontend is accessible, responsive, and TypeScript-clean | `tsc`, axe-core, and Lighthouse gates |

---

## 2. Scope

### 2.1 In Scope (Phase 1)

| Service | Endpoints tested |
|---------|-----------------|
| `user-service` | `/api/v1/auth/*`, `/api/v1/users/me*` |
| `category-service` | `/api/v1/categories*` |
| `expense-service` | `/api/v1/expenses*`, `/api/v1/tags*`, `/api/v1/recurring-expenses*` |
| `savings-goal-service` | `/api/v1/savings-goals*` |
| `budget-service` | `/api/v1/budgets*` |
| Frontend | Auth, Category, Expense, SavingsGoal, Budget feature UIs |
| Event flow | outbox → Kafka → consumer (savings-goal, budget) |

### 2.2 Out of Scope (Phase 2 — explicit deferrals)

- `income-service`, `reporting-service`, `notification-service` consumer, Dashboard charts, Notification Center UI (REQ-INC, REQ-DASH, REQ-RPT, REQ-NOTIF-004/005, O-01..O-04, O-07)
- Event consumers for notification delivery (Phase 1 producers write to outbox only)

---

## 3. Risk-Based Prioritization

### 3.1 Business Risk Matrix

| Risk | Likelihood | Impact | Priority |
|------|-----------|--------|----------|
| Authentication bypass or token reuse attack | Medium | Critical | P0 |
| Cross-user data access (403-never-404 gap) | Medium | Critical | P0 |
| Receipt EXIF metadata leak (privacy) | Low | Critical | P0 |
| Financial data corruption (wrong `amount` type / float drift) | Low | Critical | P0 |
| Budget Alert fires repeatedly per period (spam) | Medium | High | P1 |
| Goal `total_contributed` desync on concurrent events | Medium | High | P1 |
| CSV injection via import (formula execution in Excel) | Medium | High | P1 |
| Outbox row orphaned on rollback (ghost event) | Low | High | P1 |
| Duplicate Kafka delivery processed twice | Medium | High | P1 |
| RecurringExpense generates duplicate Occurrence | Low | Medium | P2 |
| Category deletion while in-use (referential integrity) | Low | Medium | P2 |
| MinIO storage ref using client filename (path traversal) | Low | High | P1 |
| PII (email, amount) exposed in log line | Medium | High | P1 |
| Hardcoded secret committed to repo | Low | Critical | P0 |
| Token Family not revoked on reuse detection | Low | Critical | P0 |
| Pixel-flood attack via crafted image | Low | Medium | P2 |

### 3.2 Risk-Driven Test Investment

| Priority | Effort allocation | Test type focus |
|----------|------------------|-----------------|
| P0 | 40% | Security, contract, targeted integration |
| P1 | 35% | Integration, event-flow, data-integrity |
| P2 | 25% | Unit, E2E happy-path, accessibility |

---

## 4. Test Levels

### Level 0 — Unit Tests

**Framework:** JUnit 5 + Mockito (backend) · Vitest + RTL (frontend)
**Scope:** One test class per production class; collaborators fully mocked; no I/O

#### Backend unit test catalogue

| Service class | Must-verify behaviours |
|--------------|----------------------|
| `JwtService` | Sign/verify round-trip; expired token rejected; wrong `typ` rejected; secret from env (not hardcoded) |
| `PiiMasker` | Email masking pattern; name masking; token → `***REDACTED***`; no `maskAmount` method exists |
| `MoneyDto` | Scale-2 string serialization; `double` construction rejected; non-INR rejected |
| `GlobalExceptionHandler` | Each mapped exception → correct HTTP status; no PII in `message` |
| `RegistrationService` | Duplicate email → `BusinessConflictException`; new user `status=INACTIVE_UNVERIFIED`; password BCrypt-hashed |
| `TokenRotationService` | Revoked token reuse → family-wide revocation; expired token → reject |
| `ExpenseService` | `amount ≤ 0` → reject; `userId` from JWT not request body; `CategoryLookupPort` called |
| `ContributionService` | SG-INV-4 (one `ContributionEntry` per `expense_id`); `total_contributed` recomputed |
| `BudgetEvaluationService` | 80% alert fires once (`fired_eighty_percent` guard); exceeded once; deactivated Budget → no event |
| `CategoryDeletionGuard` | In-use → `BusinessConflictException`; port called (not direct DB query) |
| `RecurringExpenseGenerator` | Idempotent on same `next_run_date`; `generated_count` incremented; failure → outbox event |
| `GoalProjectionService` | PAUSED/COMPLETED → `projectedCompletionDate: null`; projection formula correct on known history |

**Frontend unit tests (Vitest):**

| Component / hook | Must-verify |
|-----------------|------------|
| `axiosClient` refresh interceptor | Two concurrent 401s → exactly one `POST /auth/refresh` (MSW mock) |
| Auth store | `clearTokens()` → `getAccessToken()` returns null; token NOT in `localStorage` |
| `PaginatedTable` | Renders correct row count; pagination controls fire correct page queries |
| `MoneyDisplay` | `{amount:"100.50", currency:"INR"}` → `₹100.50` (en-IN) |
| `LoadingState` / `ErrorState` / `EmptyState` | Renders for each state; not undefined data |
| Form validation hooks | Required-field error shown before submit; `amount > 0` enforced client-side |

**Pass gate:** `mvn test -pl shared-kernel,services/*` + `vitest run` — 100% pass, ≥ 80% coverage on `security/`, `service/`, `domain/` packages.

---

### Level 1 — Integration Tests (Testcontainers)

**Framework:** JUnit 5 + Testcontainers (PostgreSQL, Kafka, MinIO real containers); `@SpringBootTest` per service
**Scope:** Full HTTP round-trip against real infra; one `*IT` class per endpoint group

#### Required Testcontainers IT classes

| Class | Endpoints covered | Key assertions beyond happy-path |
|-------|------------------|----------------------------------|
| `AuthFlowIT` | `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/verify-email` | 409 on duplicate email; 401 on expired token; family-wide revocation on reuse; BCrypt cost `$2a$12$` prefix in DB; no PII in logs |
| `PasswordResetIT` | `POST /auth/forgot-password`, `POST /auth/reset-password` | Uniform 202 for known + unknown email; all Refresh Tokens revoked on reset; second use of reset token → 400 |
| `UserProfileIT` | `GET/PUT /users/me`, `PATCH /users/me/password`, `DELETE /users/me`, `POST /users/me/data-export` | `userId` from JWT only; `passwordHash` absent from response; delete → status `DELETED`; `UserDeletedEvent` in outbox; download by foreign user → 403 |
| `CategoryIT` | `GET/POST/PUT/DELETE /categories` | DEFAULT Category delete → 409; in-use Custom delete → 409; foreign Custom → 403 (not 404); `INCOME`-type rejected for Expense creation via port; name uniqueness per owner only |
| `ExpenseCrudIT` | `GET/POST/PUT/DELETE /expenses`, `GET /expenses/{id}` | `amount ≤ 0` → 400; invisible `categoryId` → 403; foreign Expense → 403 (not 404); `OutboxEntry` present after create/update/delete; `userId` ignored in body |
| `ExpenseFilterIT` | `GET /expenses?startDate=&endDate=&categoryId=&paymentMethod=&tagId=&savingsGoalId=&sort=` | Each filter narrows correctly; cross-user isolation; `PageResponse` envelope five keys |
| `ReceiptIT` | `POST/GET/DELETE /expenses/{id}/receipt` | JPEG/PNG/WEBP → 200; PDF → 400; GIF → 400; > 5 MB → 400; ≤ 5 MB → 200; EXIF metadata: 0 segments on stored bytes; storage key is UUID path (no client filename substring); foreign Expense → 403; `Content-Disposition: inline`; `X-Content-Type-Options: nosniff` |
| `TagIT` | `GET/POST/PUT/DELETE /tags`, `GET /expenses?tagId=` | Delete detaches from Expenses without deleting them; foreign Tag → 403; filter by Tag works |
| `RecurringExpenseIT` | `POST/PUT/DELETE /recurring-expenses`, `scope=THIS`, `scope=THIS_AND_FUTURE` | Template split on `THIS_AND_FUTURE`; no duplicate Occurrence on same `next_run_date`; idempotency |
| `CsvImportIT` | `POST /expenses/import` | > 10 MB → 400; > 10,000 rows → 400; non-`text/csv` → 400; formula chars stripped (`=`, `+`, `-`, `@`); `Idempotency-Key` dedup; per-row `ImportExpensesReport`; unmatched goal name → warning, row succeeds |
| `CsvExportIT` | `GET /expenses/export` | `Content-Type: text/csv`; streaming (no OOM on 10,000-row result); injection protection in cells |
| `SavingsGoalIT` | `GET/POST/PUT/DELETE /savings-goals`, `POST /savings-goals/{id}/contributions`, `GET /savings-goals/{id}/contributions`, `PATCH /savings-goals/{id}/status` | Contribution from goal screen creates backing Expense under Savings Category; `total_contributed` updates; auto-complete fires once (idempotent); illegal status transition → 409; Expenses retained on goal delete; foreign goal → 403 |
| `ContributionReconcileIT` | Kafka consumer + DB state | Edit backing Expense amount → goal total updated; delete backing Expense → entry removed, total updated; unlink → entry removed |
| `BudgetIT` | `GET/POST/PUT/DELETE /budgets`, `PATCH /{id}/activation`, `PATCH /{id}/rollover`, `GET /{id}` | `budget_limit = 0` → 400; `categoryId` via port only; 80% alert fires once per period; exceeded fires once; deactivated Budget → zero alerts; rollover carries only when enabled; foreign Budget → 403 |
| `BudgetPeriodIT` | Scheduler + ledger | New ledger opens at period boundary; `fired_eighty_percent = false` reset; `uq_budget_period_ledgers_budget_window` prevents duplicate |
| `EventFlowIT` | real Kafka + three services | Expense created → `budget_period_ledgers.spent` updated AND `total_contributed` updated; duplicate Kafka delivery → single effect; no cross-schema SQL during execution |
| `OutboxAtomicityIT` | All write endpoints | Business transaction rollback → outbox row absent; commit → outbox row present with `published=false` |

**Pass gate:** All `*IT` classes green in CI with Testcontainers; no cross-schema SQL detected via query listener.

---

### Level 2 — Contract & API Tests

**Framework:** OpenAPI contract-diff in CI; custom `ArchUnit` rules for architecture contracts

#### 2a. OpenAPI contract stability

- The CI pipeline runs a contract-diff step on every PR: generated OpenAPI spec from current code is compared to the committed baseline.
- Any breaking change (removed endpoint, changed path, changed required field, changed status code) **fails the pipeline**.
- Non-breaking additions require a human review gate before merging.
- All 51 endpoint groups from `07-api-specification.md` are represented in the baseline spec.

#### 2b. Architecture fitness tests (`ArchUnit`)

| Rule | Failure condition |
|------|-----------------|
| No entity class may be used as a controller method parameter or return type | `@Controller` method references `@Entity` class |
| No `@Repository` bean may be injected into a `@Controller` or `@Service` other than its own service layer | Cross-layer repo injection detected |
| No service may import a class from another service's package | Cross-service package dependency |
| `Optional<T>` return types only in service layer | Any service method returns a raw `null`-able reference type |
| All monetary fields use `BigDecimal` | `double` or `float` field in any DTO or entity with money semantics |
| No `System.getenv` or hardcoded secret strings | Secret string patterns detected in source |
| Every `@Transactional` write annotated at service layer | Write method in service not annotated |

**Pass gate:** `mvn test -Dtest=ArchitectureFitnessTest` — zero violations.

#### 2c. Anti-Corruption Port contract tests

Each port (8 total from Doc 05 §8) has a dedicated contract test:

| Port | Contract test verifies |
|------|----------------------|
| `CategoryLookupPort` | Returns valid Category for DEFAULT; returns valid for owner's CUSTOM; raises on foreign CUSTOM; raises on `INCOME`-type for Expense use |
| `CategoryUsagePort` | Returns `true` when Category has ≥ 1 Expense; `false` otherwise |
| `ContributionPort` | Creates backing Expense in `expense-service` under Savings Category; payload correct |
| `ContributionEventsPort` | Correct event type for link / unlink / amount-change |
| `SpendingFeedPort` | `ExpenseCreatedEvent` payload has `categoryId`, `amount`, `userId` |
| `NotificationPort` | Event envelope valid (parked — no consumer in Phase 1) |
| `UserDataPort` | Export payload structure valid |
| `SecureNotificationDeliveryPort` | Time-limited URL generated from valid `deliveryRef` |

---

### Level 3 — End-to-End Tests

**Framework:** Playwright (preferred) or Cypress; runs against a Docker-Compose stack.
**Execution:** Nightly in CI; not on every PR (speed gate).

#### Critical User Journeys (CUJs) — must all pass for release

| CUJ-ID | Journey | Services touched |
|--------|---------|-----------------|
| CUJ-01 | Register → receive verification email (MailHog capture) → verify → login → access protected resource → logout | `user-service` |
| CUJ-02 | Login → expire Access Token → auto-refresh transparently → continue browsing without re-login | `user-service` + Frontend |
| CUJ-03 | Create Custom Category → Create Expense under it → View Expense list filtered by Category → Edit Expense → Delete Expense | `category-service` + `expense-service` |
| CUJ-04 | Upload Receipt → Verify EXIF stripped → View Receipt inline → Delete Receipt (Expense retained) | `expense-service` + MinIO |
| CUJ-05 | Create SavingsGoal → Record Contribution from goal screen → Verify backing Expense under Savings Category → Verify `total_contributed` updated → Reach target → Verify auto-complete | `savings-goal-service` + `expense-service` |
| CUJ-06 | Link existing Expense to SavingsGoal → Verify Contribution entry created → Edit Expense amount → Verify goal total updated → Unlink Expense → Verify total decreased | `expense-service` + `savings-goal-service` via Kafka |
| CUJ-07 | Create Budget (CATEGORY scope) → Record Expenses up to 80% → Verify Alert event in outbox → Record more → Verify exceeded event → Verify each fires exactly once in current period | `expense-service` + `budget-service` via Kafka |
| CUJ-08 | Create RecurringExpense (MONTHLY) → Trigger scheduler → Verify Occurrence created → Edit scope=THIS_AND_FUTURE → Verify template split | `expense-service` |
| CUJ-09 | Import CSV (valid + invalid rows + formula-injection row) → Verify per-row report → Verify injected cells sanitized → Idempotent re-import | `expense-service` |
| CUJ-10 | Delete SavingsGoal → Verify linked Expenses retained as regular Expenses without goal association | `savings-goal-service` + `expense-service` |
| CUJ-11 | Change password → Verify all Refresh Tokens revoked → Re-login required | `user-service` |
| CUJ-12 | Delete account → Verify status=DELETED → Verify login rejected → Verify `UserDeletedEvent` in outbox | `user-service` |

#### Edge cases tested E2E

| Scenario | Expected result |
|----------|----------------|
| User A attempts to access User B's Expense by guessing UUID | 403 (never 404) |
| User A attempts to access User B's SavingsGoal | 403 |
| User A attempts to delete User B's Budget | 403 |
| Refresh Token reuse after logout | 401 + entire Token Family revoked |
| Budget deactivated mid-period → new Expense arrives | No new alert event |
| Goal auto-completed → manual `PATCH status=COMPLETED` again | No second `SavingsGoalCompletedEvent` |
| CSV import with 10,001 rows | 400 with row count error |

---

### Level 4 — Regression Tests

**Scope:** Full Level 1 (Testcontainers) + CUJ-01..CUJ-12 (E2E) run on every merge to `main`.
**Flaky test policy:** Any test failing 2 consecutive times on `main` is quarantined within 24 h and tracked as a P1 defect.
**Dependency on mocks:** Architecture fitness tests flag any test that mocks `OutboxWriter` or `ProcessedEventGuard` — these must use real Testcontainers to preserve outbox atomicity assurance.

---

## 5. Security Testing

### 5.1 Authentication & Session

| Test case | REQ-* / Law | Automated? |
|-----------|-------------|------------|
| BCrypt hash in DB has prefix `$2a$12$` (cost ≥ 12) | REQ-SEC-001, SEC-1 | Yes — IT asserts DB hash prefix |
| Plaintext password absent from all log lines during register/login/reset | REQ-SEC-001, CQ-13 | Yes — log capture in IT |
| Expired Access Token → 401 on protected endpoint | REQ-SEC-002, SEC-2 | Yes — IT |
| Refresh Token stored as SHA-256 hash only (raw not in DB) | REQ-SEC-002, SEC-2 | Yes — DB assertion in IT |
| Refresh Token reuse → 401 AND all `family_id` tokens revoked | REQ-SEC-002, SEC-2 | Yes — IT `TokenRotationService` |
| JWT `typ:refresh` rejected by access-token validator | REQ-SEC-002, SEC-2 | Yes — unit test `JwtService` |
| `JWT_SECRET` loaded from env, not source | REQ-SEC-006, SEC-6 | Yes — ArchUnit regex on source |
| Login with INACTIVE_UNVERIFIED → 401 (no state disclosure) | REQ-SEC-002 | Yes — IT |
| `POST /auth/forgot-password` returns 202 for unknown email | REQ-SEC-004, SEC-4 | Yes — IT |
| Rate limit: N+1 login attempt → 429 + `Retry-After` header | REQ-SEC-004, SEC-4 | Yes — IT loop |
| `DB_PASSWORD`, MinIO credentials absent from source | REQ-SEC-006, SEC-6 | Yes — ArchUnit secret-string scan |

### 5.2 Ownership Enforcement (403-never-404)

**Rule:** For every user-owned aggregate root, a test must exist asserting that User B's request to access/modify User A's resource returns exactly 403, never 404.

| Resource | Test | Automated? |
|----------|------|------------|
| `Expense` | `GET/PUT/DELETE /expenses/{id}` as foreign user | Yes — IT |
| `Receipt` | `POST/GET/DELETE /expenses/{id}/receipt` as foreign user | Yes — IT |
| `Tag` | `DELETE /tags/{id}` as foreign user | Yes — IT |
| `RecurringExpense` | `PUT/DELETE /recurring-expenses/{id}` as foreign user | Yes — IT |
| `SavingsGoal` | `GET/PUT/DELETE /savings-goals/{id}` as foreign user | Yes — IT |
| `Budget` | `GET/PATCH /budgets/{id}` as foreign user | Yes — IT |
| `DataExport` download | `GET /users/me/data-export/{id}/download` as foreign user | Yes — IT |
| `ContributionEntry` list | `GET /savings-goals/{id}/contributions` as foreign user | Yes — IT |

### 5.3 Receipt Security

| Test case | Constitution law | Automated? |
|-----------|-----------------|------------|
| JPEG magic bytes accepted | SEC-5 | Yes — IT binary upload |
| PDF magic bytes rejected (400) | SEC-5 | Yes — IT |
| GIF magic bytes rejected (400) | SEC-5 | Yes — IT |
| PNG with wrong `.jpg` extension accepted (magic-byte sniff, not extension) | SEC-5 | Yes — IT |
| 5 MB exactly accepted; 5 MB + 1 byte rejected | SEC-5 | Yes — IT boundary |
| EXIF metadata: 0 segments on stored MinIO object after upload | SEC-5 (EXIF strip mandatory) | Yes — IT reads MinIO bytes, runs Metadata Extractor |
| Pixel-flood: image with valid magic byte but huge `width × height` rejected | SEC-5 | Yes — IT crafted PNG |
| Storage key never contains client-supplied filename substring | SEC-5 | Yes — IT asserts path pattern `receipts/{userId}/{uuid}` |
| Serve: `X-Content-Type-Options: nosniff` present | SEC-5 | Yes — IT response header |
| Serve: `Content-Disposition: inline` present | SEC-5 | Yes — IT response header |

### 5.4 CSV Security

| Test case | Automated? |
|-----------|------------|
| Cell starting with `=`, `+`, `-`, `@` has leading char stripped | Yes — IT import |
| Cell with `\t` and `\r` control characters sanitized | Yes — IT import |
| File with `text/html` MIME rejected | Yes — IT |
| File > 10 MB rejected | Yes — IT |
| File with > 10,000 rows rejected | Yes — IT |

### 5.5 PII & Log Masking

| Test case | Automated? |
|-----------|------------|
| `email` never appears raw in any log line (across all 5 services) | Yes — log-capture assertion in auth IT |
| `amount` never logged | Yes — log-capture in expense IT |
| `password_hash` / token value → `***REDACTED***` in logs | Yes — unit test `PiiMasker` |
| Error `message` in `ErrorResponse` contains no email or amount | Yes — IT asserts response body |
| `traceId` present on every log line in a request lifecycle | Yes — IT with log capture |

### 5.6 Architecture Isolation (cross-schema)

| Test case | Automated? |
|-----------|------------|
| No SQL query in `expense-service` runtime touches `category_db`, `savings_goal_db`, or `budget_db` | Yes — Testcontainers datasource proxy records queries; assertion on zero cross-service SQL |
| No SQL query in `savings-goal-service` touches `expense_db` | Yes — same proxy approach |
| No SQL query in `budget-service` touches `category_db` directly | Yes — same |
| ArchUnit: no cross-service package import | Yes — `ArchitectureFitnessTest` |

---

## 6. Accessibility Testing

**Standard:** WCAG 2.1 AA
**Tools:** `axe-core` via `@axe-core/playwright` for automated; manual screen-reader testing (NVDA + Chrome) for critical forms.

| REQ-* | Check | Automated? | Frequency |
|-------|-------|------------|-----------|
| REQ-A11Y-001 | Tab order navigates all interactive elements; no keyboard trap | Partly (Playwright tab simulation) | Per release |
| REQ-A11Y-002 | Screen reader announces all form labels, errors, loading states | Manual (NVDA) | Per release |
| REQ-A11Y-003 | Color contrast ≥ 4.5:1 for normal text, ≥ 3:1 for large text | Yes — axe-core | Every PR |
| REQ-A11Y-004 | 200% text zoom — no horizontal scroll, no content overlap | Manual | Per release |
| REQ-A11Y-005 | Form validation errors are `role="alert"` or `aria-describedby` linked | Yes — axe-core | Every PR |
| REQ-A11Y-006 | All `<img>` and icon elements have non-empty `alt` attribute | Yes — axe-core | Every PR |
| REQ-RWD-001/002 | Layout intact at 320 px, 768 px, 1024 px, 1440 px viewports | Yes — Playwright viewport test | Per release |

**Automated gate:** `axe-core` scan of every page — zero violations at `impact: serious` or `impact: critical`.

---

## 7. Performance Testing

**Tool:** Gatling (JVM) or k6; runs in a dedicated performance pipeline (not per-PR).
**Baseline environment:** Docker Compose stack, single-node each.

### 7.1 Load Targets (baseline, single-node)

| Endpoint group | Target throughput | p95 latency target |
|----------------|------------------|--------------------|
| `POST /auth/login` | 50 req/s | < 300 ms |
| `GET /expenses` (paginated, page 1) | 200 req/s | < 150 ms |
| `POST /expenses` | 100 req/s | < 250 ms |
| `GET /savings-goals/{id}` | 100 req/s | < 150 ms |
| `GET /budgets/{id}` (with derived fields) | 100 req/s | < 200 ms |
| `GET /expenses/export` (10,000-row CSV) | 10 concurrent | < 5 s total stream |
| `POST /expenses/import` (1,000-row CSV) | 5 concurrent | < 3 s |

### 7.2 Specific Performance Tests

| Test | Validates |
|------|-----------|
| CSV export streams without OOM (10,000 rows, 256 MB heap) | REQ-DB-003 (no full in-memory load) |
| Expense list with 50,000 rows — page 1 response < 200 ms | DB-4 (composite index `idx_expenses_user_date` used) |
| Outbox relay throughput: 500 events/s published to Kafka | Relay scheduler doesn't fall behind under load |
| `processed_events` guard under concurrent duplicate delivery | No race condition produces double-processing |
| Budget alert evaluation: 1,000 Expense events per second → all consumed, zero duplicate alerts | BUD-INV-5 idempotency guard holds under concurrency |
| Rate-limit test: 20 login attempts/sec from same IP → 429 on attempt 6 | REQ-SEC-004 rate limit correct |

### 7.3 Database Index Verification

The following queries must use their designated index (verified via `EXPLAIN ANALYZE` in IT):

| Query | Expected index |
|-------|---------------|
| `SELECT * FROM expenses WHERE user_id = ? ORDER BY expense_date DESC` | `idx_expenses_user_date` |
| `SELECT * FROM savings_goals WHERE user_id = ? AND status = ?` | `idx_savings_goals_user_status` |
| `SELECT * FROM budgets WHERE active = true` | `idx_budgets_active` |
| `SELECT * FROM outbox WHERE published = false ORDER BY created_at` | `idx_outbox_published_created` |
| `SELECT * FROM categories WHERE system_role = 'SAVINGS'` | `idx_categories_system_role` |

---

## 8. Observability Checks

These are verified as part of the Testcontainers integration test suite via log capture and Actuator endpoints.

| Check | How verified | Automated? |
|-------|-------------|------------|
| Every request log line contains `method`, `path`, `status`, `latencyMs`, `traceId` | `RequestLoggingFilterIT` — log capture asserts all 5 fields present | Yes |
| `traceId` is consistent across all log lines within one request lifecycle | MDC captured in log capture; assert same UUID appears in each line | Yes |
| `traceId` cleared after response (no cross-request bleed) | Two sequential requests produce distinct `traceId` values | Yes |
| No PII in any log line from any service | Log capture in auth and expense ITs; regex scan for email pattern | Yes |
| `GET /actuator/health` returns 200 with `{"status":"UP"}` for each service | IT asserts Actuator endpoint | Yes |
| Outbox relay logs each published event with `traceId` and `eventId` | Log capture in `OutboxRelayIT` | Yes |
| `RecurringGenerationFailedEvent` logged at `WARN` level | Log level assertion in `RecurringExpenseIT` | Yes |
| Log levels correct: business events at `INFO`, failures at `WARN`/`ERROR` | Log-level assertion across service ITs | Yes |

---

## 9. Data Validation and Negative Scenarios

### 9.1 Field-level validation matrix

| Entity | Field | Invalid value | Expected response |
|--------|-------|--------------|-------------------|
| `Expense` | `amount` | `-1`, `0`, `"abc"`, `null` | 400 with field error |
| `Expense` | `paymentMethod` | `"BITCOIN"`, `null` | 400 with field error |
| `Expense` | `categoryId` | non-UUID, foreign user's Category | 400 / 403 |
| `Expense` | `expenseDate` | `"not-a-date"`, future date | 400 |
| `SavingsGoal` | `targetAmount` | `0`, negative, non-numeric | 400 |
| `Budget` | `budgetLimit` | `0`, negative | 400 (BUD-INV-1) |
| `Budget` | `scope=CATEGORY` | missing `categoryId` | 400 |
| `Category` | `name` | empty string, > 100 chars | 400 |
| `Category` | `type` | `"CASHFLOW"` (unknown enum) | 400 |
| `User` | `email` | invalid format, > 255 chars | 400 |
| `User` | `password` | < 8 chars, missing uppercase | 400 |
| `Receipt` | file | 0-byte file | 400 |
| `Receipt` | size | 5,242,881 bytes | 400 |
| `RecurringExpense` | `frequency` | `"BIWEEKLY"` | 400 |
| `RecurringExpense` | `endDate` | before `anchorDate` | 400 (REC-INV) |
| `RefreshToken` | token body | expired token | 401 |
| `RefreshToken` | token body | already-revoked token | 401 |

### 9.2 Concurrency scenarios

| Scenario | Risk | Test approach |
|----------|------|--------------|
| Two requests simultaneously link different Expenses to same SavingsGoal | `total_contributed` must be consistent | Parallel Testcontainers IT threads |
| Two identical `Idempotency-Key` CSV imports arrive simultaneously | Exactly one import processed | Concurrent IT with latch |
| Outbox relay publishes event; consumer re-reads same event twice | `processed_events` guard prevents double-apply | Testcontainers Kafka consumer IT |
| `uq_budget_period_ledgers_budget_window` under concurrent period rollover | One ledger per period | DB unique constraint + IT |
| `uq_contribution_entries_goal_expense` under concurrent Contribution creation | One entry per `expense_id` | DB unique constraint + IT |

### 9.3 Boundary value tests

| Boundary | Lower | Upper |
|----------|-------|-------|
| Expense `amount` | `0.0001` (valid) / `0` (invalid) | `NUMERIC(19,4)` max |
| Receipt size | 1 byte (valid) | 5,242,880 bytes (valid) / 5,242,881 (invalid) |
| CSV rows | 1 row (valid) | 10,000 rows (valid) / 10,001 (invalid) |
| CSV size | 1 byte | 10 MB (valid) / 10 MB + 1 byte (invalid) |
| Access Token TTL | 1 second before expiry (valid) | Exactly at expiry (invalid) |
| Refresh Token TTL | 7 days − 1 second (valid) | 7 days (invalid) |
| Password length | 8 chars (valid) | 72 chars (BCrypt limit — document behaviour) |
| Tag count per Expense | 1 tag | No upper bound specified — document actual constraint |

---

## 10. Test Environment and Infrastructure

### 10.1 Environment matrix

| Environment | Purpose | Data | Trigger |
|-------------|---------|------|---------|
| `local-dev` | Developer TDD loop | Testcontainers ephemeral | Manual / on save |
| `ci` | PR gate | Testcontainers ephemeral | Every commit |
| `integration` | Full Docker Compose stack | Seed fixtures | Every merge to `main` |
| `performance` | Load/stress testing | Synthetic dataset (100k Expenses per user) | Weekly + pre-release |
| `staging` | Pre-release E2E + manual exploratory | Anonymised snapshot | Pre-release cut |

### 10.2 Testcontainers configuration

Each service's `*IT` class uses:

```java
@Testcontainers
class ExpenseCrudIT {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("expense_db");

    @Container
    static MinIOContainer minio = new MinIOContainer("minio/minio:latest");

    // EventFlowIT also starts:
    @Container
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
}
```

**Rules:**
- Each `*IT` class owns its own container; no shared static state between test classes.
- Flyway migrations run on container startup (same path as production).
- Containers started once per class (`@Container` on `static` field); not per test method.
- Each `@Test` rolls back via `@Transactional` or explicit cleanup in `@AfterEach`.

### 10.3 MinIO test configuration

- Local MinIO bucket `test-receipts` created in `@BeforeAll`.
- EXIF assertion: download stored object bytes; run `metadata-extractor` library; assert 0 EXIF directories.
- Storage key pattern assertion: `assertThat(storedKey).matches("receipts/[uuid]/[uuid]")`.

### 10.4 Kafka test configuration

- `KafkaContainer` (real Confluent Kafka) used for `EventFlowIT`, `ContributionReconcileIT`, `BudgetIT`.
- Consumer uses `auto.offset.reset=earliest` and a test-specific `group.id` per run.
- Latch-based wait: `CountDownLatch(expectedMessages)` with 10-second timeout.

### 10.5 MSW (Mock Service Worker) for frontend

- All API calls in frontend tests use MSW handlers.
- MSW server started in `beforeAll`, reset in `afterEach`.
- No real backend called from Vitest or RTL tests.
- A separate Playwright E2E suite runs against the real Docker Compose stack.

---

## 11. Test Data Strategy

### 11.1 Fixtures and builders

Use **builder pattern** for test data construction — never raw constructors in test bodies:

```java
// Example: ExpenseTestBuilder
Expense expense = new ExpenseTestBuilder()
    .withUserId(USER_A_UUID)
    .withAmount(new BigDecimal("500.00"))
    .withPaymentMethod(PaymentMethod.UPI)
    .withCategoryId(FOOD_CATEGORY_UUID)
    .withExpenseDate(LocalDate.of(2026, 6, 1))
    .build();
```

**Rules:**
- No test may depend on another test's inserted data (REQ-TEST-004: test independence).
- Foreign key cascade in Testcontainers — truncate in reverse-dependency order in `@BeforeEach`.
- UUIDs generated deterministically from a test seed (not `UUID.randomUUID()`) where cross-test traceability is needed.

### 11.2 Seed data categories

| Category | Fixture | Used by |
|----------|---------|---------|
| DEFAULT Categories | `V999__test_seed_categories.sql` (test profile only) | `CategoryIT`, `ExpenseCrudIT`, `CsvImportIT` |
| Savings Category UUID | Constant `TestConstants.SAVINGS_CATEGORY_UUID` | `ContributionService` tests |
| Pre-verified User A / User B | `UserTestFixture.createVerifiedUser()` helper | All ownership-enforcement tests |
| Pre-created Expense with Receipt | `ExpenseTestFixture.expenseWithReceipt()` | `ReceiptIT` |
| Pre-created RecurringExpense template | `RecurringTestFixture.monthlyTemplate()` | `RecurringExpenseIT` |

### 11.3 Sensitive data in tests

| Data type | Test approach |
|-----------|--------------|
| Passwords | Use `TestConstants.VALID_PASSWORD` = `"TestP@ss12"` — never commit production passwords |
| JWT secret | `TestConstants.TEST_JWT_SECRET` = 256-bit hex string — different from production value |
| MinIO credentials | `minio` / `minio123` in Testcontainers only |
| Email addresses | `testuser-{uuid}@test.local` pattern — no real email domain |
| Amounts | Precise `BigDecimal` literals — never `double` or `float` |

### 11.4 Performance dataset

For load tests, a synthetic dataset generator creates:
- 10 users per run
- 100,000 Expenses per user distributed across 24 months
- 5 SavingsGoals per user with varied `GoalStatus`
- 3 Budgets per user (1 OVERALL, 2 CATEGORY)
- Generated via `DataSetGenerator` JUnit 5 extension before Gatling scenarios run

---

## 12. Automation Strategy

### 12.1 Automate — always

| Test type | Tool | When runs |
|-----------|------|----------|
| Backend unit tests | JUnit 5 + Mockito | Every commit |
| Frontend unit tests | Vitest + RTL | Every commit |
| Architecture fitness | ArchUnit | Every commit |
| OpenAPI contract-diff | CI pipeline step | Every PR |
| Backend integration (Testcontainers) | JUnit 5 + Testcontainers | Every PR |
| Accessibility scan | axe-core + Playwright | Every PR |
| Security: BCrypt cost, PII masking, 403-never-404, receipt controls | Testcontainers IT | Every PR |
| Performance regression (p95 gate) | Gatling / k6 | Weekly + pre-release |
| Cross-schema SQL detection | Testcontainers query proxy | Every PR |

### 12.2 Manual — always

| Activity | Rationale | Frequency |
|----------|-----------|----------|
| Exploratory testing of new feature flows | Automation covers known paths; exploration finds unknown failure modes | Each feature release |
| Screen-reader testing (NVDA + Chrome) | Automated tools miss semantic announcements and focus management | Each release |
| Visual regression (pixel-comparison) | Design intent drift not caught by axe-core | Each design change |
| Security penetration test (OWASP ZAP manual scan) | Automated scans miss business-logic flaws | Pre-release |
| i18n / locale `en-IN` formatting | Date/number formatting subtle in real browser | Each locale change |
| Load test interpretation | p95 numbers require human judgement on capacity | Pre-release |
| Receipt image quality validation | Pixel content cannot be auto-asserted | Spot-check |

### 12.3 Decision heuristic

> **Automate** if: deterministic, runs > once, result is binary (pass/fail), fast (< 60 s), no human perception needed.
> **Manual** if: judgement required, visual/sensory, exploratory, or environmental complexity outweighs automation value.

### 12.4 Test pyramid shape

```
              ┌──────────────┐
              │  E2E (12 CUJs) │   ~5% — nightly
            ┌─┴──────────────┴─┐
            │   Integration IT   │   ~25% — every PR
          ┌─┴──────────────────┴─┐
          │  Contract / ArchUnit  │   ~10% — every PR
        ┌─┴──────────────────────┴─┐
        │    Unit (per class)       │   ~60% — every commit
        └──────────────────────────┘
```

---

## 13. Acceptance Criteria for Test Completion

### 13.1 Phase gate — must pass before release

| Gate | Criterion | Blocking? |
|------|-----------|-----------|
| G-01 | `mvn test` on all 5 services + `shared-kernel`: 100% pass | Yes |
| G-02 | `vitest run`: 100% pass; `tsc --noEmit`: 0 errors; 0 `any` types | Yes |
| G-03 | All Testcontainers ITs green (list in §4 Level 1) | Yes |
| G-04 | ArchUnit architecture fitness: 0 violations | Yes |
| G-05 | OpenAPI contract-diff: 0 breaking changes vs. committed baseline | Yes |
| G-06 | All 12 CUJs (§4 Level 3) green in Docker Compose E2E | Yes |
| G-07 | 403-never-404 ownership test: 100% of aggregate roots covered, all asserting 403 | Yes |
| G-08 | EXIF strip verified: `metadata-extractor` returns 0 EXIF segments on stored receipt bytes | Yes — SEC-5 release blocker per Doc 10 §5.3 |
| G-09 | PII log scan: 0 occurrences of raw email, raw amount, raw password in any service log | Yes |
| G-10 | Cross-schema SQL: 0 queries from any service targeting another service's database | Yes |
| G-11 | No `null` return in any service-layer method (ArchUnit + spot grep) | Yes |
| G-12 | No hardcoded secret in source (ArchUnit regex; `git-secrets` scan) | Yes |
| G-13 | `GET /actuator/health` → 200 `{"status":"UP"}` for all 5 services in Docker Compose | Yes |
| G-14 | axe-core scan: 0 `serious` or `critical` violations on all pages | Yes |
| G-15 | Performance: p95 ≤ targets in §7.1 under baseline load | No (advisory; blocks only if > 2× target) |
| G-16 | REQ-* traceability: every Phase-1 REQ-* has ≥ 1 associated passing test | Yes |

### 13.2 Coverage targets

| Package | Minimum line coverage |
|---------|-----------------------|
| `*/service/**` | 85% |
| `*/security/**` | 90% |
| `*/domain/**` | 80% |
| `*/controller/**` | 70% (IT covers controller; unit lower) |
| `*/repository/**` | 60% (Testcontainers covers queries) |
| Frontend `src/` | 70% (RTL + Vitest) |

> Coverage targets are **floors**, not goals. A test that hits coverage but doesn't assert meaningful behaviour fails the quality bar.

### 13.3 Defect exit criteria

| Severity | Must be resolved before release? |
|----------|----------------------------------|
| Blocker (Constitution violation, auth bypass, data loss, EXIF not stripped) | Yes — release blocked |
| Critical (403 not returned for ownership violation, PII in log, float used for money) | Yes — release blocked |
| Major (wrong status code, alert fires twice, missing pagination envelope) | Yes |
| Minor (UI label incorrect, non-critical UX issue) | No — document and schedule |

---

## 14. Test Execution Summary

| Trigger | Tests run | Max duration |
|---------|----------|--------------|
| Every commit (local) | Unit (backend + frontend) | < 2 min |
| Every PR (CI) | Unit + Integration + ArchUnit + Contract-diff + axe-core | < 15 min |
| Every merge to `main` | All PR tests + CUJ E2E (Docker Compose) | < 30 min |
| Nightly | All `main` tests + full Playwright CUJ suite | < 60 min |
| Weekly | All nightly + Performance (Gatling) + OWASP ZAP scan | < 3 h |
| Pre-release | All weekly + manual exploratory + screen-reader + visual regression | 1 day |

---

## 15. REQ-* to Test-Level Traceability (summary)

| REQ-* group | Primary test level | Key test class(es) |
|-------------|-------------------|--------------------|
| REQ-USR-001..011 | L1 Integration | `AuthFlowIT`, `PasswordResetIT`, `UserProfileIT` |
| REQ-CAT-001..005 | L1 Integration | `CategoryIT` |
| REQ-EXP-001..014 | L1 Integration + L3 E2E | `ExpenseCrudIT`, `ReceiptIT`, `CsvImportIT`, `CsvExportIT`, CUJ-03, CUJ-04, CUJ-09 |
| REQ-REC-001..006 | L1 Integration + L3 E2E | `RecurringExpenseIT`, CUJ-08 |
| REQ-GOAL-001..013 | L1 Integration + L3 E2E | `SavingsGoalIT`, `ContributionReconcileIT`, CUJ-05, CUJ-06, CUJ-10 |
| REQ-TAG-001..003 | L1 Integration | `TagIT` |
| REQ-BUD-001..007 | L1 Integration + L3 E2E | `BudgetIT`, `BudgetPeriodIT`, CUJ-07 |
| REQ-SEC-001..006 | L0 Unit + L1 Security | `JwtService` unit, `AuthFlowIT`, `ReceiptIT`, ArchUnit |
| REQ-API-001..007 | L2 Contract + L1 Integration | OpenAPI contract-diff, all `*IT` classes |
| REQ-CQ-001..008 | L2 ArchUnit | `ArchitectureFitnessTest` |
| REQ-TEST-001..004 | L0 + L1 | All unit + IT classes |
| REQ-OBS-001..006 | L1 Integration | `RequestLoggingFilterIT`, Actuator checks |
| REQ-DB-001..003 | L1 Integration + Perf | `EXPLAIN ANALYZE` in IT, `CsvExportIT` streaming |
| REQ-FE-001..006 | L0 Frontend + L3 E2E | `axiosClient` Vitest, Playwright E2E |
| REQ-A11Y-001..006 | axe-core + Manual | Playwright axe scan, NVDA manual |
| REQ-RWD-001..002 | L3 E2E | Playwright multi-viewport |
| REQ-UX-001..006 | L3 E2E + Manual | CUJ flows, exploratory session |

*End of `14-test-strategy.md` — Daily Expense Application Production-Grade Test Strategy.*

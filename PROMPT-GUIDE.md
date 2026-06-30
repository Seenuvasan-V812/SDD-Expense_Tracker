# Prompt Guide — SpecKit `tasks` → `analyze` → `implement`

> **Project:** Daily Expense Application
> **Feature directory:** `specs/001-daily-expense-tracker`
> **Covers:** the **`/speckit-tasks`**, **`/speckit-analyze`**, and **`/speckit-implement`** phases.
> `/speckit-plan` is already done (`plan.md`, `research.md`, `data-model.md`, `contracts/`,
> `quickstart.md` exist and were verified).
>
> **End goal:** leave the toolchain in a state where `/speckit-implement` — run as a **dependency-ordered
> sequence of gated batches** — produces a **fully working, production-ready** Phase-1 application, not a
> skeleton. The implement phase is **not** one giant run; see Phase 3 below for why and how.

---

## My intent (read this first — the plan behind the prompts)

The whole design hinges on one verified fact about *this* repo's wiring:

> `/speckit-implement` reads **only** `12-implementation-plan.md` + `plan.md` + `data-model.md` +
> `contracts/` + `research.md` + `quickstart.md` + `constitution.md`. It does **not** open
> `13-task-breakdown.md` or `14-test-strategy.md`.
> *(Confirmed: `implement` SKILL.md step 3, and `common.ps1:191` which maps the `TASKS` variable to
> `12-implementation-plan.md`.)*

Everything that makes the build production-grade — per-task acceptance criteria (in `13`), the named
test classes / ArchUnit rules / release gates `G-01..G-16` (in `14`) — lives in files the implementer
will never read. So:

- **`/speckit-tasks` intent:** *fold it all in.* Rewrite `12-implementation-plan.md` into one
  **self-sufficient, executable** task list — reconciling the existing `T001–T111` with the acceptance
  criteria from `13`, the test obligations from `14`, the per-task constitution gates, and US/REQ
  traceability — so the implementer needs nothing else. TDD (RED→GREEN→REFACTOR) is encoded per task,
  and the 16 release gates become the final Definition of Done. Deferred Phase-2 scope is excluded.
- **`/speckit-analyze` intent:** *prove it's enough.* This is the last gate before implement. It must
  judge the task list not merely for internal consistency but for **whether it is sufficient to build a
  production-ready app** — coverage of every REQ-* + `SC-01..SC-05` + release gate, no scope leakage, no
  constitution `MUST` violations, glossary fidelity, and the exact task-format hygiene the implement
  parser depends on. Read-only; nothing is modified.

If both run as prompted, `/speckit-implement` can execute the plan top-to-bottom with full test coverage
and no missing production concerns.

---

## Repo-specific gotchas these two prompts neutralize

| # | Gotcha | Consequence if unguided | How the prompt handles it |
|---|--------|-------------------------|---------------------------|
| 1 | **`TASKS` is remapped** to `12-implementation-plan.md` (`common.ps1:191`); stock `speckit-tasks` says "generate `tasks.md`". | A stray `tasks.md` the rest of the toolchain ignores; `analyze`/`implement` read the old file. | `tasks` prompt writes into `12-implementation-plan.md` and reconciles `T001–T111`. |
| 2 | **`implement` reads neither `13` nor `14`.** | Acceptance criteria + test classes + release gates never reach the build → skeleton, not production-ready. | `tasks` prompt folds `13`/`14` detail into `12-implementation-plan.md`; `analyze` flags anything left only in `13`/`14`. |
| 3 | **`spec.md` is a MANIFEST** (Section 6 → 15 sources), not the spec. | Coverage analysis runs against summaries and misses real REQ-*/invariants. | `analyze` prompt resolves requirements through the numbered files. |
| 4 | **Phase-2 deferrals** (`spec.md` Section 7): income, reporting, notification **consumer**. | Tasks for unbuildable scope; wasted/incorrect implementation. | Both prompts carry the scope guard; `analyze` marks leakage CRITICAL. |
| 5 | **`implement` halts on incomplete checklists** (`checklists/`). | A surprise stop mid-implement. | `analyze` prompt reports checklist completeness (all 3 are currently `[x]`). |

---

## Pre-flight (run once before `/speckit-tasks`)

```
.specify/scripts/powershell/check-prerequisites.ps1 -PathsOnly
```

Verify:
- `FEATURE_DIR` ends with `specs/001-daily-expense-tracker`
- `IMPL_PLAN` ends with `...\plan.md` (must already exist)
- `TASKS` ends with `...\12-implementation-plan.md`  ← proves the remap is active

---

## Phase 1 — `/speckit-tasks`

**Intent:** turn `12-implementation-plan.md` into the single self-sufficient, executable, test-first task
list that `/speckit-implement` can build a production-ready app from — folding in the acceptance criteria
(`13`), test obligations + release gates (`14`), per-task constitution gates, and US/REQ traceability.

### Command + argument

```
/speckit-tasks
```

```
OUTPUT TARGET (verified in .specify/scripts/powershell/common.ps1 line 191): the SpecKit TASKS
artifact resolves to 12-implementation-plan.md, NOT tasks.md. /speckit-analyze and /speckit-implement
both read 12-implementation-plan.md. Write the generated task list INTO 12-implementation-plan.md.
Do NOT create a stray tasks.md — the toolchain would ignore it. Reconcile with the existing
T001–T111 and the Section 3 review gates; do not discard them.

GOAL: produce a SINGLE, SELF-SUFFICIENT, EXECUTABLE task list. /speckit-implement reads ONLY
12-implementation-plan.md + plan.md + data-model.md + contracts/ + research.md + quickstart.md +
constitution.md — it does NOT open 13-task-breakdown.md or 14-test-strategy.md. Therefore every
detail needed for a production-ready build (acceptance criteria, test obligations, quality gates)
MUST be folded into 12-implementation-plan.md now.

READ FIRST: plan.md and its design artifacts (data-model.md, contracts/, research.md, quickstart.md);
spec.md Section 1 (user stories US-01..US-07) and Section 5 (SC-01..SC-05); 13-task-breakdown.md
(TASK-001..111 with Acceptance Criteria, Spec Refs, Depends-On); 12-implementation-plan.md (canonical
phases + T### IDs + Section 3 gates); 14-test-strategy.md (Level-0 unit catalogue, the named
Testcontainers *IT classes, ArchUnit fitness rules, the 8 Anti-Corruption Port contract tests, the 12
CUJs, security/a11y/performance tests, release gates G-01..G-16, coverage floors);
11-agent-instruction-pack.md (3-Commit Loop + hard-stop triggers); .specify/memory/constitution.md
(per-task laws). Use 02-glossary.md terms exclusively; anti-terms are prohibited.

ORGANIZATION:
- Keep the canonical phase model and order: Phase 0 shared-kernel -> 1 user -> 2 category+expense ->
  3 savings+budget -> 4 outbox infra -> 5 frontend. Preserve T001–T111 and the Depends-On order from 13.
- DUAL-LABEL every task: its user story (US-01..US-07) AND its REQ-* / aggregate-invariant ID
  (INV-/EXP-INV-/SG-INV-/BUD-INV-/REC-INV-) taken from 13-task-breakdown.md. Setup/foundational/
  cross-cutting tasks carry the governing Constitution law id instead of a US label.
- STRICT checklist format (the implement parser needs this exactly):
  `- [ ] T### [P?] [US?/law] <description> — exact/file/path` using the monorepo layout in Section 1.
  Add [P] ONLY where files are disjoint AND all Depends-On are satisfied.
- Attach to each task a one-line Acceptance Criterion (condensed from 13) so implement has a binary
  done-check.

TDD IS MANDATORY (constitution 3-Commit Loop, P6, SC-05): for EVERY implementation task, emit its RED
test task FIRST, naming the concrete artifact from 14-test-strategy.md — Mockito unit per service class
(Level-0 catalogue), Testcontainers IT per endpoint group (the named *IT classes), ArchUnit fitness
rules, OpenAPI contract-diff, the 8 port contract tests, frontend Vitest/RTL/MSW. Each task is one
RED -> GREEN -> REFACTOR (REFACTOR adds MDC tracing + DTO mapping + cleanup, no behaviour change).

PRODUCTION-READINESS — add these as explicit tasks/gates, never leave implicit: outbox atomicity
(OutboxAtomicityIT: rollback leaks no event); idempotent consumers (processed_events dedup on eventId);
EXIF strip asserted (0 segments on stored bytes); 403-never-404 for every aggregate root; PII log-scrub;
cross-schema-SQL detector (zero cross-service queries); BCrypt cost >= 12 asserted; rate-limit
429 + Retry-After; receipt magic-byte + <=5 MB + pixel-flood guard; CSV <=10 MB/10k rows + injection
strip; axe-core a11y; en-IN Money/Date; /actuator/health UP. Encode release gates G-01..G-16 from
14-test-strategy.md as the FINAL Definition-of-Done checklist, and PRESERVE the existing Section 3
per-phase review gates.

PER-TASK QUALITY GATES (must hold on every task — constitution + Doc 11): no service returns null
(Optional<T>, CQ-2); no JPA entity on a controller signature (AL-4); business logic only in service
layer (CQ-1); every write @Transactional with the outbox write in the SAME tx (CQ-8); Money is
NUMERIC(19,4)/BigDecimal, never double (DB-5); traceId on every log line, 0 PII (CQ-12/13); no magic
literals (CQ-3); secrets from env only (SEC-6); versioned /api/v1 paths (API-1); pagination + error
envelopes (API-2/3); TS strict, no any (FE-3).

SCOPE GUARD (spec.md Section 7): Phase-1 services ONLY (user, category, expense, savings-goal, budget).
Generate NO tasks for income-service, reporting-service / Dashboard / Reports, or any notification
CONSUMER / Notification Center. Producers STILL write notification/reporting events to the outbox —
keep those producer tasks; add no consumer.

REPORT: rewrite 12-implementation-plan.md as the reconciled self-sufficient list; then report total
task count, count per US-01..US-07, parallel [P] opportunities, and the suggested MVP slice
(User Story 1 -> user-service: Phase 0 + Phase 1).
```

### Expected outcomes
- **`12-implementation-plan.md` rewritten in place** as the single self-sufficient task artifact —
  `T001…T111` preserved and reconciled, each in the strict `- [ ] T### [P?] [US/law] desc — path` format.
- **Every implementation task is preceded by its RED test task**, naming a concrete test class/rule from
  `14-test-strategy.md`.
- **Every task carries** a US (`US-01..US-07`) + REQ-*/invariant label, an exact monorepo file path, and a
  one-line acceptance criterion.
- **Release gates `G-01..G-16`** appear as the final Definition of Done; the existing **Section 3 per-phase
  review gates are preserved**.
- **No tasks for deferred Phase-2 scope.**
- Completion report: total count, per-story breakdown, `[P]` opportunities, MVP = User Story 1.

### Verify before moving on
- [ ] Output landed in **`12-implementation-plan.md`** (no stray `tasks.md` created).
- [ ] Each task: checkbox + ID + `[P]` where parallel + US/law label + concrete file path + acceptance line.
- [ ] A RED test task exists for every implementation task (3-Commit Loop).
- [ ] Acceptance criteria from `13` and the named `*IT` classes / ArchUnit rules / `G-01..G-16` from `14`
      are now *inside* `12-implementation-plan.md` (not only in `13`/`14`).
- [ ] No income / reporting / notification-**consumer** tasks; outbox **producers** retained.
- [ ] Glossary terms only; no anti-terms.

---

## Phase 2 — `/speckit-analyze`

**Intent:** the final pre-implementation gate. Prove the task list is **sufficient to build a
production-ready app**, not just internally consistent — coverage, scope, constitution compliance, and
the task-format hygiene the implementer parses. **Read-only: it modifies nothing.**

> Hard-wired to compare `spec.md` ↔ `plan.md` ↔ **`12-implementation-plan.md`**, and requires `plan.md`
> to exist — so run `/speckit-plan` and `/speckit-tasks` first.

### Command + argument

```
/speckit-analyze
```

```
Run the read-only cross-artifact consistency and quality analysis over spec.md, plan.md, and
12-implementation-plan.md (the wired TASKS file), with .specify/memory/constitution.md as the
non-negotiable authority. Modify NOTHING.

This is the LAST gate before /speckit-implement, and implement builds ONLY from 12-implementation-plan.md
+ plan.md + design artifacts. So judge the task list by whether it is SUFFICIENT TO PRODUCE A
PRODUCTION-READY app — not merely whether it is internally consistent.

RESOLVE THE MANIFEST: spec.md is an entry point only. Build the requirements inventory from the numbered
sources it lists: REQ-* with MoSCoW + Phase split (03), SC-01..SC-05 (spec.md Section 5), domain
invariants INV-1..10 and EXP-/SG-/BUD-/REC-INV (05/06), the 51 endpoint groups (07), the 45 events (08),
the 16-table schema (09), security controls (10), and the test obligations + release gates G-01..G-16 (14).

PRIORITIZE THESE CHECKS:
- COVERAGE: every Phase-1 REQ-* and each SC-01..SC-05 maps to >=1 task (zero-coverage -> CRITICAL/HIGH).
  Confirm each named Testcontainers *IT class and each of the 8 Anti-Corruption Port contract tests from
  14 has a corresponding task; flag missing test coverage.
- SELF-SUFFICIENCY: flag any task whose acceptance criterion or test obligation exists only in 13/14 and
  was NOT carried into 12-implementation-plan.md (implement will not read 13/14) -> HIGH.
- SCOPE LEAKAGE: any milestone/task targeting income / reporting / notification-CONSUMER (spec.md
  Section 7) -> CRITICAL. Producers emitting outbox events are allowed.
- CONSTITUTION MUST VIOLATIONS -> automatically CRITICAL: AL-1 cross-schema access, AL-4 entity on
  controller, API-2/3 envelopes, API-4 status codes incl. 403-never-404 (SEC-3), CQ-2 Optional/no-null,
  CQ-8 transactional outbox, DB-5 Money type, SEC-1 BCrypt>=12, SEC-2 rotation/family-revoke,
  SEC-5 receipt EXIF/magic-byte, FE-3 no-any.
- GLOSSARY FIDELITY (02): flag any anti-term in plan/tasks; canonical names only.
- TASK HYGIENE for the implement parser: every task has checkbox + sequential ID + [P] where parallel +
  US/REQ label + exact file path; a RED test precedes each implementation task; no ordering contradiction
  (e.g. integration before its foundational setup); no untestable acceptance criterion.
- CHECKLIST GATE: report completion status of the files in checklists/ (implement halts on any incomplete).

OUTPUT: the compact findings table (<=50 rows: ID, Category, Severity, Location, Summary, Recommendation),
the requirement -> task coverage summary, constitution-alignment issues, unmapped tasks, and the metrics
block (total requirements, total tasks, coverage %, ambiguity count, duplication count, critical count).
End with Next Actions — resolve all CRITICAL and HIGH before /speckit-implement. Offer remediation but
APPLY NOTHING without my explicit approval.
```

### Expected outcomes
- A **findings table** (≤50 rows; ID/Category/Severity/Location/Summary/Recommendation), overflow summarized.
- A **coverage summary** mapping each requirement key (REQ-* / SC-*) → task IDs, with zero-coverage flagged.
- **Constitution-alignment** issues, **unmapped tasks**, **checklist completeness**, and a **metrics** block.
- A **Next Actions** block + an offer to draft remediation (applies nothing automatically).

### Act on the report
- [ ] Resolve every **CRITICAL** and **HIGH** finding before `/speckit-implement`.
- [ ] Confirm **100% REQ-* + SC-01..SC-05 coverage** and **0 scope-leakage / 0 constitution-MUST** findings.
- [ ] Re-run `/speckit-analyze` after fixes to confirm a clean report.

---

## Phase 3 — `/speckit-implement` (run phase by phase)

**Why not one run:** 119 tasks × a 3-commit TDD loop each will exhaust a single context/token budget long
before the app is built, and would leave you with no green checkpoint to resume from. So implement in
**13 small, self-contained runs**, each ending in a **test gate that must be green before the next run**.
Each prompt below is **complete and ready to paste** — no placeholders. `/speckit-implement` flips
`- [ ]`→`- [x]` in `12-implementation-plan.md`, so runs are resumable: a fresh chat picks up exactly where
the last `[x]` left off.

**These standing rules hold for EVERY run** (the implement agent already loads `constitution.md` +
`12-implementation-plan.md`, so they bind even though the prompts state them tersely): read only
`12-implementation-plan.md` + `plan.md` + `data-model.md` + `contracts/` + `research.md` + `quickstart.md`
+ `.specify/memory/constitution.md` (v1.2.0); **TDD 3-Commit Loop** per task — `test(RED)` (the named test,
must fail first) → `feat(GREEN)` → `refactor(REFACTOR)` (MDC traceId + DTO map, no behaviour change); honor
every constitution `MUST` (CQ-2 Optional/no-null, AL-4 no entity in payloads, CQ-8 outbox-in-same-tx,
DB-5 BigDecimal money, SEC-3 403-never-404, CQ-12/13 traceId+0-PII, API-1/2/3 versioning+envelopes,
FE-3 no-`any`, FE-7 registry); **glossary terms only**; **scope guard** — build nothing for income /
reporting / notification-**consumer** (producers that write outbox events stay).

**Sizing & order logic:** the two heaviest phases (user-service 24 tasks, expense-service 20 tasks) and the
frontend are each split into two runs at a compilable seam; the **outbox infra (Run 7) is pulled in front of
the savings/budget consumers** because `T076`/`T089`/`T119` all `Depend: T097`; the cross-service event-flow
run (Run 10) comes **after** goal+budget exist because `EventFlowIT` needs them.

| Run | Tasks | Area | Green gate to pass before continuing |
|-----|-------|------|--------------------------------------|
| 1 | T001–T015 | shared-kernel + infra | `mvn test -pl shared-kernel` 100%; `docker compose up` healthy; ArchUnit scaffolded |
| 2 | T016–T030 | user-service — auth & token core | register→verify→login→refresh→logout subset of `AuthFlowIT` green; BCrypt `$2a$12$` |
| 3 | T031–T039 | user-service — lifecycle & profile | `AuthFlowIT`+`PasswordResetIT`+`UserProfileIT` green; family-revoke; 429+Retry-After |
| 4 | T040–T048 | category-service | `CategoryIT` green; defaults seeded; DEFAULT edit/delete→403/409; `CategoryLookupPort` test |
| 5 | T049–T059 | expense-service — CRUD + emitters | `ExpenseCrudIT`+`ExpenseFilterIT` green; EXP-INV-5; 403-never-404; port emitters wired |
| 6 | T060–T068 | expense-service — receipts/tags/recurring/CSV | `ReceiptIT`/`TagIT`/`RecurringExpenseIT`/`CsvImportIT`/`CsvExportIT` green; **EXIF 0 segments** |
| 7 | T094–T098 | outbox infra (pulled forward) | `OutboxAtomicityIT` green; `processed_events` dedup proven on dup `eventId` |
| 8 | T069–T082 | savings-goal-service | `SavingsGoalIT`+`ContributionReconcileIT` green; auto-complete once; dual contribution flow |
| 9 | T083–T093 | budget-service | `BudgetIT`+`BudgetPeriodIT` green; alert fires once/period/threshold incl. Kafka redelivery |
| 10 | T099, T119 | cross-service event flow | `EventFlowIT`+`SavingsGoalDeletedConsumeIT` green |
| 11 | T100–T105 | frontend — scaffold/axios/auth/shared | `tsc --noEmit` 0 `any`; single-flight refresh proven (MSW); FE-7 registry clean |
| 12 | T106–T111 | frontend — feature UIs + a11y | `vitest run` 100%; axe-core 0 serious/critical; loading/error/empty per view |
| 13 | T112–T118 | data export + observability | Phase 6 gate **+ final release gates G-01..G-16** |

---

### Run 1 — shared-kernel & infra (T001–T015)

```
/speckit-implement
```

```
Build ONLY tasks T001 through T015 from 12-implementation-plan.md, in Depends-On order. This is the
foundation every service imports, so correctness here is non-negotiable. For each task run the 3-Commit
Loop (write the named RED test first — PageResponseTest, ErrorResponseTest, GlobalExceptionHandlerTest,
MoneyDtoTest, JwtServiceTest, JwtAuthenticationFilter test, TraceIdFilterTest, RequestLoggingFilter log
capture, PiiMaskerTest, OutboxEntryTest — then minimal GREEN, then REFACTOR). Key correctness points:
JwtService HS256 with sub=UUID (never email), expired+wrong-typ rejected, JWT_SECRET from env (SEC-6);
MoneyDto rejects double and non-INR (DB-5); PiiMasker has NO maskAmount method; ErrorResponse/PageResponse
serialize EXACTLY their specified keys; GlobalExceptionHandler maps 400/401/403/404/409/429 to the uniform
envelope with 0 PII. T003 must scaffold the per-service ArchitectureRulesTest (G-04 rules) and wire it into
CI before Testcontainers. When T001–T015 are all [x], run the Phase 0 gate: `docker compose up` healthy
(postgres x5 + minio + kafka + zookeeper + mailhog), `mvn test -pl shared-kernel` 100% pass, >=80% coverage
on security/, GlobalExceptionHandler contract test green. Then HALT and report each completed task with its
RED test name, the gate result per item, and confirm "next: Run 2 (T016–T030)". Build nothing beyond T015.
```
**✅ Gate:** Phase 0 — shared-kernel suite 100%, infra healthy, ArchUnit wired.

---

### Run 2 — user-service: auth & token core (T016–T030)

```
/speckit-implement
```

```
Build ONLY tasks T016 through T030 from 12-implementation-plan.md, in Depends-On order. This is the
identity core every later story depends on. 3-Commit Loop per task; RED tests include the Flyway migration
tests (V1 users .. V5 data_exports), UserRepositoryTest, RegistrationServiceTest, PasswordEncoderConfigTest,
and the register/verify/login/refresh/logout cases of AuthFlowIT. Security MUSTs: BCrypt cost >=12 (hash
prefix $2a$12$), passwords/hashes never logged or returned; login refuses INACTIVE_UNVERIFIED with a
generic 401; refresh-token rotation — a reused (already-revoked) token returns 401 AND revokes the entire
family_id; only SHA-256 token hashes are stored; UserRegisteredEvent is written to the outbox in the SAME
@Transactional as the email_verifications row (rollback leaves neither). 403-never-404 on any /{id}. When
T016–T030 are all [x], run the partial gate: the register→verify→login→refresh→reuse(family-revoke)→logout
path of AuthFlowIT green; BCrypt asserted in DB. Then HALT and report completed tasks + RED test names +
gate result, and confirm "next: Run 3 (T031–T039)". Build nothing beyond T030.
```
**✅ Gate:** AuthFlowIT auth-core path green; family-revoke + BCrypt≥12 proven.

---

### Run 3 — user-service: lifecycle, profile, data export (T031–T039)

```
/speckit-implement
```

```
Build ONLY tasks T031 through T039 from 12-implementation-plan.md, in Depends-On order; T016–T030 are
already [x] — trust them. 3-Commit Loop per task; RED tests include PasswordResetIT, UserProfileIT,
UserDataExportIT, the rate-limit loop in AuthFlowIT, and the token-cleanup scheduler test. MUSTs:
forgot-password returns a uniform 202 with NO account enumeration and writes PasswordResetRequestedEvent to
the outbox; password reset AND password change revoke ALL refresh tokens; DELETE /users/me sets status
DELETED, revokes refresh tokens, and writes UserDeletedEvent in the same tx (rollback leaves neither), and a
later login returns 401; GET/PUT /users/me ignore any body userId (identity from JWT, AL-5) and never expose
passwordHash; data-export download is 200 for owner / 403 for foreign and download_ref is never logged; auth
rate-limit returns 429 with an integer Retry-After. When T031–T039 are all [x], run the Phase 1 gate:
AuthFlowIT + PasswordResetIT + UserProfileIT all green; 403-never-404 on every /{id} and /me; lists isolate
by user; contract-diff clean. Then HALT and report, confirming "next: Run 4 (T040–T048)". Nothing beyond T039.
```
**✅ Gate:** Phase 1 — full user-service IT suite green; 403-never-404; rate-limit enforced.

---

### Run 4 — category-service (T040–T048)

```
/speckit-implement
```

```
Build ONLY tasks T040 through T048 from 12-implementation-plan.md, in Depends-On order. 3-Commit Loop per
task; RED tests include the categories migration test, the enum/serialization test, the DefaultCategorySeeder
IT, CategoryIT (create/edit/protection/filter), the CategoryLookupPort contract test, and the
CategoryDeletionGuard test. Do NOT parallelize T043/T044/T045 — they all touch CategoryAuthoringService.
MUSTs: seed >=11 non-deletable DEFAULT categories including the Savings Category (system_role=SAVINGS,
user_id=NULL) and make re-seed idempotent; custom category name is unique per owner (dup→409); editing or
deleting a DEFAULT →403/409; a foreign custom category →403 (never 404); deletion of an in-use category →409
via CategoryUsagePort with NO cross-service SQL (AL-1); CategoryLookupPort rejects INCOME-type for expense
use and hides foreign custom categories. When T040–T048 are all [x], run the category gate: CategoryIT green;
seeding, DEFAULT protection, in-use-blocking, and cross-service lookup all proven. Then HALT and report,
confirming "next: Run 5 (T049–T059)". Build nothing beyond T048.
```
**✅ Gate:** CategoryIT green; defaults + protection + `CategoryLookupPort` proven.

---

### Run 5 — expense-service: CRUD, filters, event emitters (T049–T059)

```
/speckit-implement
```

```
Build ONLY tasks T049 through T059 from 12-implementation-plan.md, in Depends-On order. 3-Commit Loop per
task; RED tests include the expenses/recurring migrations, the Expense entity test, ExpenseService unit +
ExpenseCrudIT, ExpenseFilterIT, OutboxAtomicityIT, and the ContributionEventsPort / SpendingFeedPort
contract tests. MUSTs: amount<=0 →400, missing required field →400 naming the field, foreign category →403,
userId from JWT only; EXP-INV-5 — when savingsGoalId is present the categoryId MUST be the Savings Category
(else 400); Expense holds Set<UUID> tag ids and a plain UUID savings_goal_id (no @ManyToOne, INV-9);
GET /expenses returns the 5-key PageResponse with from/to/categoryId/paymentMethod/tagId/savingsGoalId
filters and cross-user isolation; GET/PUT/DELETE /{id} is 403-never-404 and DELETE cascades the Receipt;
Expense create/update/delete each write to the outbox in the SAME tx (rollback leaves no event); the two
ports emit to the outbox with no foreign-schema SQL (AL-1). When T049–T059 are all [x], run the gate:
ExpenseCrudIT + ExpenseFilterIT green; EXP-INV-5 and 403-never-404 proven; outbox atomicity green. Then HALT
and report, confirming "next: Run 6 (T060–T068)". Build nothing beyond T059.
```
**✅ Gate:** ExpenseCrudIT + ExpenseFilterIT green; EXP-INV-5, 403-never-404, outbox atomicity proven.

---

### Run 6 — expense-service: receipts, tags, recurring, CSV (T060–T068)

```
/speckit-implement
```

```
Build ONLY tasks T060 through T068 from 12-implementation-plan.md, in Depends-On order; T049–T059 are [x].
3-Commit Loop per task; RED tests include ReceiptIT, TagIT, RecurringExpenseIT, CsvImportIT, CsvExportIT,
the CategoryLookupHttpAdapter unit test, and the consolidated ExpenseIT suite. SECURITY MUSTs (these are
release blockers): receipt upload validates by server-side magic-byte (not Content-Type) — accept
JPEG/PNG/WEBP, reject pdf/gif and >5 MB with 400, enforce the <=25 MP pixel-flood guard, strip EXIF so the
STORED bytes contain 0 EXIF segments, and store under server-generated key receipts/{userId}/{uuid};
GET receipt streams with Content-Disposition: inline + X-Content-Type-Options: nosniff, foreign→403, DELETE
removes object+row but retains the Expense; tag delete detaches without deleting Expenses; recurring
generation creates the next Occurrence, copies tags, advances next_run_date, is idempotent on the same date,
and raises RecurringGenerationFailedEvent on failure; CSV import enforces <=10 MB / <=10000 rows, strips
formula-injection chars `= + - @ tab CR`, honors Idempotency-Key for dedup, and returns a per-row
SUCCEEDED/FAILED/SUCCEEDED_WITH_WARNING report; CSV export streams text/csv with no full in-memory load
(CQ-10). When T060–T068 are all [x], run the Phase 2 gate: ExpenseIT (all of ReceiptIT/TagIT/
RecurringExpenseIT/CsvImportIT/CsvExportIT) green; EXIF 0 segments asserted on stored bytes; CSV injection
neutralized; Idempotency-Key dedup proven. Then HALT and report, confirming "next: Run 7 (T094–T098)".
Nothing beyond T068.
```
**✅ Gate:** Phase 2 — expense-service IT suite green; **EXIF 0 segments**, CSV injection + idempotency proven.

---

### Run 7 — outbox infrastructure (T094–T098)

```
/speckit-implement
```

```
Build ONLY tasks T094 through T098 from 12-implementation-plan.md, in Depends-On order. This is pulled
AHEAD of the savings/budget services on purpose: their consumers (T076, T089) and the goal-deleted consumer
(T119) all depend on T097 (the processed_events guard). 3-Commit Loop per task; RED tests include the
per-service outbox migration tests, OutboxAtomicityIT, the relay IT against real Kafka (Testcontainers), the
processed_events dedup IT, and EventEnvelopeTest. MUSTs: each service has its OWN outbox table (no shared
table, AL-1) with uq on event_id; OutboxWriter inserts the event within the caller's @Transactional and a
rollback leaves no row; OutboxRelayScheduler polls published=false via index, publishes the EventEnvelope to
Kafka, sets published+published_at, and skips already-published rows; processed_events uses event_id PK with
insert-before-process so a duplicate Kafka delivery produces a single effect; EventEnvelope carries all 8
fields and round-trips identically. Also add the processed_events migration to expense-service (needed by
T119). When T094–T098 are all [x], run the gate: OutboxAtomicityIT green (rollback leaks no event), relay IT
publishes to a real topic, dedup IT proves single-effect on dup eventId. Then HALT and report, confirming
"next: Run 8 (T069–T082)". Build nothing beyond T098.
```
**✅ Gate:** OutboxAtomicityIT + relay IT + dedup IT green; rollback leaks no event.

---

### Run 8 — savings-goal-service (T069–T082)

```
/speckit-implement
```

```
Build ONLY tasks T069 through T082 from 12-implementation-plan.md, in Depends-On order; the outbox infra
(T094–T098) is already [x]. 3-Commit Loop per task; RED tests include the savings_goals /
contribution_entries migration tests, the SavingsGoal entity test, SavingsGoalIT (CRUD + history +
lifecycle + auto-complete), and ContributionReconcileIT against real Kafka. MUSTs: primary contribution flow
creates a backing Expense via ContributionPort under the Savings Category with NO expense_db SQL (AL-1) and
entry source=GOAL_SCREEN; the uq(goal,expense) prevents duplicate entries; secondary flow consumes
ExpenseLinkedToSavingsGoalEvent idempotently (dup eventId→single insert) with source=LINKED_EXPENSE;
reconciliation updates total on amount-adjust and removes the entry on unlink/delete, idempotent via
processed_events; SG-INV-6 auto-complete fires EXACTLY once when total>=target while ACTIVE (already
COMPLETED→no second event; two concurrent reconciles crossing target→one COMPLETED, one event) via the
status-checked @Transactional guard; illegal status transitions →409; expense_id is a plain UUID;
SavingsGoalDeletedEvent is written to the outbox on delete but Expenses are NOT cascaded; 403-never-404
everywhere. When T069–T082 are all [x], run the gate: SavingsGoalIT + ContributionReconcileIT green; both
contribution flows, reconcile, single auto-complete, and Expense-retention-on-delete proven. Then HALT and
report, confirming "next: Run 9 (T083–T093)". Build nothing beyond T082.
```
**✅ Gate:** SavingsGoalIT + ContributionReconcileIT green; auto-complete-once + dual flow proven.

---

### Run 9 — budget-service (T083–T093)

```
/speckit-implement
```

```
Build ONLY tasks T083 through T093 from 12-implementation-plan.md, in Depends-On order. 3-Commit Loop per
task; RED tests include the budgets / budget_period_ledgers migration tests, the Budget entity test, BudgetIT
(CRUD + activation + status), BudgetPeriodIT (rollover), and — critically — BudgetAlertKafkaRedeliveryTest.
THE highest-regression rule (BUD-INV-5): each threshold (EIGHTY_PERCENT, EXCEEDED) fires AT MOST ONCE per
period — set the fired_* flag and write BudgetAlertFiredEvent in the SAME tx; repeats and DUPLICATE Kafka
redelivery of the same ExpenseCreatedEvent produce NO extra event (proven by BudgetAlertKafkaRedeliveryTest
via processed_events + the flag). Other MUSTs: CATEGORY scope requires categoryId (validated via
CategoryLookupPort, AL-2), OVERALL forbids it, limit<=0→400; a deactivated Budget fires no alerts and
reactivation resumes (BUD-INV-7); the spending consumer matches by userId+categoryId (CATEGORY) or userId
(OVERALL), updates spent idempotently, and NEVER reads the expense schema (BUD-INV-4); rollover carries
unspent into the next ledger only when enabled, resets fired_* flags, and the period close/open is idempotent
(uq prevents duplicate ledgers); 403-never-404. When T083–T093 are all [x], run the Phase 3 gate: BudgetIT +
BudgetPeriodIT green; one 80% + one exceeded across repeats and redeliveries; deactivated fires none; rollover
only when enabled. Then HALT and report, confirming "next: Run 10 (T099, T119)". Nothing beyond T093.
```
**✅ Gate:** Phase 3 — BudgetIT + BudgetPeriodIT green; alert fires once/period/threshold under redelivery.

---

### Run 10 — cross-service event flow (T099, T119)

```
/speckit-implement
```

```
Build ONLY tasks T099 and T119 from 12-implementation-plan.md. All five services and the outbox infra are
already [x]; this run proves they work TOGETHER. 3-Commit Loop; RED tests: EventFlowIT and
SavingsGoalDeletedConsumeIT, both against real Kafka via Testcontainers. T099 (EventFlowIT): creating an
Expense propagates so the budget ledger `spent` AND the savings goal `total_contributed` both update, and a
DUPLICATE delivery yields a single effect, with zero cross-schema SQL (AL-1). T119
(SavingsGoalDeletedEventConsumer in expense-service): consuming SavingsGoalDeletedEvent runs
UPDATE expenses SET savings_goal_id=NULL WHERE savings_goal_id=:goalId AND user_id=:userId — Expenses are
NOT deleted — idempotent via the expense-service processed_events table (dup eventId → no double update) and
with NO savings_goal_db SQL. When both are [x], run the Phase 4 gate: EventFlowIT + SavingsGoalDeletedConsumeIT
green; ArchUnit AL-1 (no cross-schema SQL) green. Then HALT and report, confirming "next: Run 11 (T100–T105)".
Build nothing else.
```
**✅ Gate:** Phase 4 — EventFlowIT + SavingsGoalDeletedConsumeIT green; AL-1 clean.

---

### Run 11 — frontend: scaffold, axios, auth, shared components (T100–T105)

```
/speckit-implement
```

```
Build ONLY tasks T100 through T105 from 12-implementation-plan.md, in Depends-On order. This is the React 18
+ TypeScript (strict) foundation. 3-Commit Loop per task; RED tests/gates include the tsc --noEmit gate +
FE-7 registry lint (T100), the apiConfig grep gate, the axiosClient Vitest with MSW (T102), the auth-store
Vitest (T103), the auth-pages RTL tests (T104), and RTL for all 6 shared components (T105). MUSTs:
`npx shadcn@latest init` committed (components.json) with Tailwind PostCSS configured; EVERY UI/styling/
charting/form package in package.json appears in 15-ui-design-system.md §3 (FE-7) — no recharts in Phase 1;
exactly ONE Axios instance and base URL from import.meta.env.VITE_API_BASE_URL only (no hardcoded http://);
the refresh interceptor is single-flight — two concurrent 401s trigger exactly ONE POST /auth/refresh, queued
requests replay, refresh-fail clears tokens + redirects; the access token lives in memory (never
localStorage/sessionStorage); shared components map per Doc 15 §5 — LoadingState=shadcn Skeleton (aria-busy),
ErrorState=shadcn Alert, EmptyState=shadcn Card+lucide, PaginatedTable=@tanstack/react-table+shadcn Table
consuming PageResponse<T>, MoneyDisplay via Intl.NumberFormat('en-IN',{style:'currency',currency:'INR'}),
DateDisplay via date-fns enIN; strict TypeScript, zero `any`. When T100–T105 are all [x], run the gate:
`tsc --noEmit` 0 errors / 0 any; `vitest run` for these components 100%; single-flight refresh proven via MSW;
registry-compliance lint clean. Then HALT and report, confirming "next: Run 12 (T106–T111)". Nothing beyond T105.
```
**✅ Gate:** `tsc` clean (0 `any`); single-flight refresh proven; FE-7 registry clean.

---

### Run 12 — frontend: feature UIs + accessibility (T106–T111)

```
/speckit-implement
```

```
Build ONLY tasks T106 through T111 from 12-implementation-plan.md, in Depends-On order; the frontend
foundation (T100–T105) is [x]. 3-Commit Loop per task; RED tests include RTL per feature, axe-core +
viewport tests (T110), and the consolidated Vitest+RTL+MSW suite (T111). MUSTs: every data view renders
explicit loading/error/empty states (FE-4) and every form validates client-side before submit (FE-5);
Categories shows DEFAULT as non-deletable + the ?type= filter; Expenses has filters/sort/pagination, an
amount/date/category/method-validated form, receipt pre-validation (>5 MB / wrong type warned before
submit), and import/export controls; Savings Goals splits ACTIVE/COMPLETED, shows remaining/percent/
projection + paginated history, and a contribution form validating amount>0; Budgets shows status cards
(set/spent/remaining/percentUsed), a limit>0 form, and activation/rollover PATCH toggles; accessibility =
ARIA roles + keyboard nav (Tab/Enter/Esc) + WCAG AA contrast with 0 serious/critical axe-core violations,
rendering at 320/768/1024px; reuse Run-11 shared components and the single axiosClient (no new fetch/Axios);
strict TS, no `any`. When T106–T111 are all [x], run the Phase 5 gate: `vitest run` 100%; `tsc --noEmit`
clean; axe-core 0 serious/critical on all pages. Then HALT and report, confirming "next: Run 13 (T112–T118)".
Build nothing beyond T111.
```
**✅ Gate:** Phase 5 — `vitest run` 100%; axe-core clean; states + validation per view.

---

### Run 13 — data export aggregation, observability & final release gates (T112–T118)

```
/speckit-implement
```

```
Build ONLY tasks T112 through T118 from 12-implementation-plan.md, in Depends-On order. 3-Commit Loop per
task; RED tests include UserDataExportIT (aggregation path), UserDataPortContractTest (per adapter), the four
internal /internal/users/{userId}/export-data endpoint tests (T114–T117, [P] across services), and the
business-metrics counter assertions (T118). MUSTs: UserDataPort fans out to all four service adapters + the
local user profile, builds the ZIP, uploads to MinIO, sets export status READY, and writes DataExportReadyEvent
to the outbox in the same tx (CQ-8); EACH adapter returns a non-null UserExportSegment (empty, not error, for
a user with no data) with ArchUnit confirming 0 cross-service DB reads (AL-1); internal endpoints are guarded
by a service token; the expense adapter streams (no full in-memory load, CQ-10); Micrometer counters
(expenses.created, users.registered, budget.alerts.sent, goals.completed) increment after their events with 0
PII in tags. When T112–T118 are all [x], run the Phase 6 gate AND the FINAL release gates G-01..G-16 from
12-implementation-plan.md Section 3: G-01 mvn test (5 services + shared-kernel) 100%; G-02 vitest 100% +
tsc 0 any; G-03 all Testcontainers ITs green; G-04 ArchUnit 0 violations; G-05 OpenAPI contract-diff 0
breaking; G-06 all 12 CUJs green in Docker Compose E2E; G-07 403-never-404 on every aggregate; G-08 EXIF 0
segments; G-09 PII log scan 0; G-10 cross-schema SQL 0; G-11 no null returns; G-12 no hardcoded secrets;
G-13 /actuator/health UP on all 5; G-14 axe-core 0 serious/critical; G-15 perf p95 (advisory); G-16 every
Phase-1 REQ-* has >=1 passing test. Then HALT and report the full G-01..G-16 result and confirm Phase-1 is
production-ready, with Income/Reporting/Notification-consumer NOT implemented (spec.md §7).
```
**✅ Gate:** Phase 6 gate **+ final release gates G-01..G-16** all green (G-15 advisory).

---

### How to run the sequence
- Run them **in order, one chat per run** (or `/clear` between runs) to keep each within budget.
- A run **halts at its gate** — make the gate **green** (tests *passing*, not just written) before starting
  the next. If a gate fails, fix within the same run; do not advance on red.
- Resuming is automatic: the next run only touches still-`[ ]` tasks, because completed tasks are `[x]` in
  `12-implementation-plan.md`.
- Order is load-bearing: **Run 7 (outbox) precedes Runs 8–9** (consumers need `T097`); **Run 10 follows**
  them (`EventFlowIT` needs goal + budget).

---

## Order & watch-list

1. **Pre-flight** — `check-prerequisites.ps1 -PathsOnly` (confirm `TASKS` → `12-implementation-plan.md`).
2. **`/speckit-tasks`** — rewrite `12-implementation-plan.md` into the self-sufficient, test-first list.
3. **`/speckit-analyze`** — read-only sufficiency + consistency report; fix CRITICAL/HIGH; re-run.
4. **`/speckit-implement`** — run the **13 gated runs (Run 1→13) in order** (Phase 3); each ends on a green
   test gate before the next starts. Run 7 (outbox infra) precedes Runs 8–9; Run 10 (event flow) follows them.

**Watch-list**
- **Output file:** tasks must land in `12-implementation-plan.md`, never a new `tasks.md`.
- **Self-sufficiency:** if acceptance criteria / test classes / release gates remain only in `13`/`14`,
  the implementer won't see them — fold them in.
- **Deferral guard:** no income / reporting / notification-**consumer** work; outbox **producers** are fine.
- **Glossary:** canonical names only; anti-terms are violations.
- **One run per phase, halt on the gate:** never advance on a red gate; one chat (or `/clear`) per run to
  stay within token budget; runs resume automatically from the last `[x]`.
- **Dependency order beats phase number:** Run 7 outbox infra (T094–T098) before the consumers in Runs 8–9
  (T076/T089/T119 need T097); Run 10 (T099/T119) only after goal+budget services exist.

---

*End of PROMPT-GUIDE.md — covers `tasks`, `analyze`, and `implement` (13 gated runs, one per phase/seam).*

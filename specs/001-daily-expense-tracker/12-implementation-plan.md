# Implementation Plan — Daily Expense Application (AI-Agent Execution Blueprint)

| Field | Value |
|-------|-------|
| **Document** | `12-implementation-plan.md` — Deterministic Execution Task List (Architect + Phase Sequencing) |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Governing Authority** | [Engineering Constitution](../../.specify/memory/constitution.md) + [Doc 11 Agent Pack](./11-agent-instruction-pack.md) |
| **Status** | Ready for execution |
| **Created** | 2026-06-26 |

> **Mandate to the AI coding agent.** This is your exact execution order. Do **not** reorder, skip,
> or batch tasks. Each task is one unit of the **3-Commit Loop**: **RED** (write the failing
> Testcontainers/Mockito test first) → **GREEN** (minimal implementation to pass) → **REFACTOR**
> (MDC tracing, DTO mapping, trace-map, cleanup). A task is complete only when its phase Review Gate
> (§3) is green. Every non-negotiable principle (AL-1…AL-5, API-1…API-7, SEC-1…SEC-6, CQ-1…CQ-14)
> is binding at all times.

**Active scope (Phase 1 of product):** `user-service`, `category-service`, `expense-service`,
`savings-goal-service`, `budget-service`.
**Deferred to Phase 2 (do NOT build):** Income, Reporting, **Notification** bounded contexts.
Where an active service must raise a notification/email, it publishes the domain event to the
outbox only — there is **no consumer** in scope; the event is parked for the Phase-2 Notification context.

**Legend:** `[P]` = parallelizable (no dependency on the immediately preceding task).
Task format: `- [ ] T[XXX] [Service] [Trace ID] Description — exact/file/path`

---

## 1. Architectural & Repository Scaffolding

### 1.1 Monorepo folder structure

```text
daily-expense-app/
├── pom.xml                          # parent aggregator POM (Java 21, Spring Boot 3.x BOM)
├── docker-compose.yml               # postgres×5, minio, kafka, zookeeper, mailhog
├── .github/workflows/ci.yml         # build · tsc · lint · unit · integration · contract-diff
├── shared-kernel/                   # published as a versioned internal library (NO domain logic)
│   ├── pom.xml
│   └── src/main/java/com/dailyexpense/shared/
│       ├── api/
│       │   ├── PageResponse.java            # uniform pagination envelope (API-2)
│       │   ├── ErrorResponse.java           # uniform error envelope (API-3)
│       │   └── ApiError.java
│       ├── exception/
│       │   ├── GlobalExceptionHandler.java  # @ControllerAdvice base (API-3)
│       │   ├── ResourceNotFoundException.java
│       │   ├── ForbiddenOwnershipException.java   # → 403 (SEC-3)
│       │   └── BusinessConflictException.java      # → 409
│       ├── money/MoneyDto.java               # BigDecimal + currency (DB-5)
│       ├── security/
│       │   ├── JwtService.java               # HS256 sign/verify (SEC-2)
│       │   ├── JwtAuthenticationFilter.java  # sets SecurityContext + MDC (AL-5)
│       │   └── CallerContext.java            # callerUserId accessor
│       ├── observability/
│       │   ├── TraceIdFilter.java            # MDC traceId per request (CQ-12)
│       │   ├── RequestLoggingFilter.java     # method/path/status/latency (CQ-11)
│       │   └── PiiMasker.java                # email/name/amount masking (CQ-13)
│       └── outbox/
│           ├── OutboxEntry.java              # mapped @MappedSuperclass / embeddable
│           └── OutboxPublisher.java          # interface (impl per service)
├── services/
│   ├── user-service/
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/java/com/dailyexpense/user/
│   │       │   ├── UserServiceApplication.java
│   │       │   ├── controller/        # AuthController, UserController
│   │       │   ├── service/           # business logic ONLY (CQ-1)
│   │       │   ├── repository/        # Spring Data JPA (returns Optional, CQ-2)
│   │       │   ├── domain/            # JPA entities (never serialized, AL-4)
│   │       │   ├── dto/               # *Request / *Response records (AL-4)
│   │       │   ├── port/              # SecureNotificationDeliveryPort, UserDataPort
│   │       │   ├── security/          # service-specific security config
│   │       │   └── scheduler/         # TokenCleanupScheduler
│   │       ├── main/resources/
│   │       │   ├── application.yml
│   │       │   └── db/migration/      # Flyway V1__*.sql … (CQ-9)
│   │       └── test/java/com/dailyexpense/user/   # Mockito (unit) + Testcontainers (it)
│   │   ├── category-service/   (same layout)
│   │   ├── expense-service/    (same layout + storage/ for MinIO adapter)
│   │   ├── savings-goal-service/ (same layout + consumer/ for expense events)
│   │   └── budget-service/     (same layout + consumer/ for expense events)
└── frontend/
    ├── package.json                  # React 18 + TS strict
    ├── tsconfig.json                 # "strict": true (P2/FE-3)
    ├── vite.config.ts
    └── src/
        ├── app/                      # router, queryClient, providers
        ├── lib/                      # axiosClient (single instance, FE-1/2), apiConfig (FE-6)
        ├── features/                 # auth, categories, expenses, savings-goals, budgets
        ├── components/               # LoadingState/ErrorState/EmptyState (FE-4), PaginatedTable
        ├── hooks/
        └── types/                    # TS mirrors of API DTOs
```

**Isolation rules baked into scaffolding:** each service has its **own** `db/migration` and its
**own** datasource; `shared-kernel` contains **zero** domain logic and **zero** repository code
(prevents accidental cross-context coupling — AL-1/AL-3). Cross-context reads go through `port/`
adapters only (AL-2).

### 1.2 Database migration strategy

- **Tool:** **Flyway**, one isolated migration history **per service database** (no shared schema).
- **Naming:** `V<n>__<verb>_<noun>.sql` (e.g., `V1__create_users.sql`, `V2__create_refresh_tokens.sql`).
- **Mandatory column conventions enforced by a migration review checklist (CQ-9, DB-5, DB-8):**
  - Every PK: `id UUID PRIMARY KEY` (application-generated; no DB sequences).
  - Every table: `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`.
  - `updated_at` maintained by a shared `set_updated_at()` trigger created in each DB's `V1`.
  - All money: `NUMERIC(19,4)` (+ `currency VARCHAR(3) DEFAULT 'INR'`); **never** `float`/`double`.
  - All enums: `VARCHAR` + `CHECK (col IN ('UPPER_SNAKE',…))` (DB-7).
  - All filter/join columns indexed (DB-4/CQ-10); cross-service refs are bare `UUID`, **no FK** (DB-2/AL-1).
- **Policy:** migrations are additive and backward-compatible; new `NOT NULL` columns always carry a `DEFAULT` (or are backfilled in the same migration). Flyway runs on startup with `validate-on-migrate=true`.

---

## 2. Sequential Execution Task List

### Phase 0 — Foundation & Shared Kernel

> Builds the cross-cutting machinery every service depends on. No business domain yet.

- [ ] T001 [Repo] [P0] Initialize parent Maven aggregator POM (Java 21, Spring Boot 3.x BOM, JUnit5, Mockito, Testcontainers) — `pom.xml`
- [ ] T002 [Repo] [P0] Author Docker Compose (postgres×5 isolated, minio, kafka+zookeeper, mailhog) — `docker-compose.yml`
- [ ] T003 [Repo] [P0] CI pipeline: build → `tsc` → lint → unit → Testcontainers → OpenAPI contract diff gate — `.github/workflows/ci.yml`
- [ ] T004 [Shared] [API-2] PageResponse<T> uniform pagination envelope — `shared-kernel/.../api/PageResponse.java`
- [ ] T005 [Shared] [API-3] ErrorResponse + ApiError uniform error envelope (no PII in message, CQ-13) — `shared-kernel/.../api/ErrorResponse.java`
- [ ] T006 [Shared] [API-3] GlobalExceptionHandler @ControllerAdvice mapping 400/401/403/404/409/429 — `shared-kernel/.../exception/GlobalExceptionHandler.java`
- [ ] T007 [Shared] [SEC-3] ForbiddenOwnershipException + ResourceNotFoundException + BusinessConflictException — `shared-kernel/.../exception/`
- [ ] T008 [Shared] [DB-5] MoneyDto (BigDecimal scale-2 + currency) with serialization contract — `shared-kernel/.../money/MoneyDto.java`
- [ ] T009 [Shared] [SEC-2] JwtService — HS256 sign/verify, claims sub/iat/exp/jti/typ, 15-min access TTL — `shared-kernel/.../security/JwtService.java`
- [ ] T010 [Shared] [AL-5] JwtAuthenticationFilter — extract `sub`→CallerContext + MDC userId — `shared-kernel/.../security/JwtAuthenticationFilter.java`
- [ ] T011 [Shared] [CQ-12] TraceIdFilter — generate/propagate `traceId` into MDC per request — `shared-kernel/.../observability/TraceIdFilter.java`
- [ ] T012 [Shared] [CQ-11] RequestLoggingFilter — method/path/status/latency/traceId — `shared-kernel/.../observability/RequestLoggingFilter.java`
- [ ] T013 [Shared] [CQ-13] PiiMasker — mask email/name; omit amounts/tokens — `shared-kernel/.../observability/PiiMasker.java`
- [ ] T014 [Shared] [CQ-8] OutboxEntry + OutboxPublisher interface (transactional outbox contract) — `shared-kernel/.../outbox/`
- [ ] T015 [Shared] [P0] [P] Unit tests for JwtService, PiiMasker, MoneyDto (RED→GREEN) — `shared-kernel/.../test/`

### Phase 1 — Identity & Access (`user-service`)

> User aggregate, BCrypt, JWT issuance, Token Family rotation, full auth + account lifecycle.

- [ ] T016 [user-service] [DB-8] Flyway V1 — `users` + `set_updated_at()` trigger (status/locale/timezone/weekly_digest_enabled) — `.../db/migration/V1__create_users.sql`
- [ ] T017 [user-service] [SEC-2] Flyway V2 — `refresh_tokens` with `family_id UUID NOT NULL` + indexes — `.../db/migration/V2__create_refresh_tokens.sql`
- [ ] T018 [user-service] [REQ-USR-004] Flyway V3 — `email_verifications` — `.../db/migration/V3__create_email_verifications.sql`
- [ ] T019 [user-service] [REQ-USR-007] Flyway V4 — `password_reset_tokens` — `.../db/migration/V4__create_password_reset_tokens.sql`
- [ ] T020 [user-service] [REQ-USR-011] Flyway V5 — `data_exports` — `.../db/migration/V5__create_data_exports.sql`
- [ ] T021 [user-service] [REQ-USR-001] User JPA entity + UserStatus enum — `.../domain/User.java`
- [ ] T022 [user-service] [CQ-2] UserRepository (Spring Data; Optional returns) — `.../repository/UserRepository.java`
- [ ] T023 [user-service] [REQ-USR-003] RED+GREEN+REFACTOR: RegistrationService + register DTOs (reject dup email 409, status INACTIVE_UNVERIFIED) — `.../service/RegistrationService.java`
- [ ] T024 [user-service] [SEC-1] BCrypt password encoder config (cost ≥ 12) — `.../security/PasswordEncoderConfig.java`
- [ ] T025 [user-service] [REQ-USR-004] EmailVerificationService + `UserRegisteredEvent`(deliveryRef) via outbox — `.../service/EmailVerificationService.java`
- [ ] T026 [user-service] [REQ-USR-004] AuthController `POST /auth/register`, `GET /auth/verify-email` — `.../controller/AuthController.java`
- [ ] T027 [user-service] [REQ-USR-005] AuthenticationService `POST /auth/login` (refuse unverified, issue access+refresh) — `.../service/AuthenticationService.java`
- [ ] T028 [user-service] [SEC-2] RefreshToken entity + RefreshTokenRepository (`findByFamilyId`, SHA-256 hash store) — `.../domain/RefreshToken.java`
- [ ] T029 [user-service] [REQ-SEC-002] TokenRotationService `POST /auth/refresh` — rotate; reuse → **family-wide revocation** — `.../service/TokenRotationService.java`
- [ ] T030 [user-service] [REQ-USR-006] `POST /auth/logout` (revoke session token) — `.../controller/AuthController.java`
- [ ] T031 [user-service] [REQ-USR-007] Password reset: forgot + reset endpoints + `PasswordResetRequestedEvent` via outbox — `.../service/AccountLifecycleService.java`
- [ ] T032 [user-service] [REQ-USR-008] UserController `GET/PUT /users/me` (profile + weeklyDigestEnabled + locale) — `.../controller/UserController.java`
- [ ] T033 [user-service] [REQ-USR-009] `PATCH /users/me/password` (verify current, re-hash) — `.../controller/UserController.java`
- [ ] T034 [user-service] [REQ-USR-010] `DELETE /users/me` + broadcast `UserDeletedEvent` (cascade) — `.../service/AccountLifecycleService.java`
- [ ] T035 [user-service] [REQ-USR-011] Data Export `POST /users/me/data-export` (202) + signed `GET .../download` — `.../service/DataExportService.java`
- [ ] T036 [user-service] [Doc 05 §8] SecureNotificationDeliveryPort impl (resolve deliveryRef → one-time URL) — `.../port/SecureNotificationDeliveryPort.java`
- [ ] T037 [user-service] [SEC-4] Auth rate-limit filter (per-IP + per-account) → 429 + Retry-After — `.../security/AuthRateLimitFilter.java`
- [ ] T038 [user-service] [Doc 10 §2.8] TokenCleanupScheduler (purge expired/revoked) — `.../scheduler/TokenCleanupScheduler.java`
- [ ] T039 [user-service] [Doc 04 §2] Full BDD integration suite (Testcontainers): register→verify→login→refresh→reuse→logout — `.../test/.../AuthFlowIT.java`

### Phase 2 — Core Domains (`category-service` & `expense-service`)

> Categories (Default seed + Custom), Expense aggregate, receipts (5 MB, EXIF strip), tags, recurring, CSV.

#### category-service

- [ ] T040 [category-service] [REQ-CAT-001] Flyway V1 — `categories` (+ trigger, constraints, partial savings index) — `.../db/migration/V1__create_categories.sql`
- [ ] T041 [category-service] [REQ-CAT-001] Category entity + CategoryType/Origin/SystemRole enums — `.../domain/Category.java`
- [ ] T042 [category-service] [REQ-CAT-001] DefaultCategorySeeder ApplicationRunner (11 defaults; Savings system_role) — `.../initializer/DefaultCategorySeeder.java`
- [ ] T043 [category-service] [REQ-CAT-002] CategoryAuthoringService + `GET/POST/PUT /categories` (Custom name unique per owner) — `.../service/CategoryAuthoringService.java`
- [ ] T044 [category-service] [REQ-CAT-003] Block edit/delete of DEFAULT (403); ownership on Custom — `.../service/CategoryAuthoringService.java`
- [ ] T045 [category-service] [REQ-CAT-005] CategoryDeletionGuard via CategoryUsagePort (409 if in use) — `.../service/CategoryDeletionGuard.java`
- [ ] T046 [category-service] [AL-2] CategoryLookupPort endpoint (validate id+visibility+type) consumed by expense/budget — `.../port/CategoryLookupController.java`
- [ ] T047 [category-service] [REQ-CAT-004] `?type=` filter + `GET /categories/{id}` — `.../controller/CategoryController.java`
- [ ] T048 [category-service] [Doc 04] Integration suite (Testcontainers): default protection, in-use delete 409, uniqueness — `.../test/.../CategoryIT.java`

#### expense-service

- [ ] T049 [expense-service] [DB-4] Flyway V1 — `expenses` (+ trigger, all indexes, composite user_date) — `.../db/migration/V1__create_expenses.sql`
- [ ] T050 [expense-service] [EXP-INV-7] Flyway V2 — `receipts` (unique expense_id, size CHECK) — `.../db/migration/V2__create_receipts.sql`
- [ ] T051 [expense-service] [REQ-TAG-001] Flyway V3 — `tags` + `expense_tags` join — `.../db/migration/V3__create_tags.sql`
- [ ] T052 [expense-service] [REQ-REC-001] Flyway V4 — `recurring_expenses` — `.../db/migration/V4__create_recurring_expenses.sql`
- [ ] T053 [expense-service] [INV-9] Expense entity (holds `Set<TagId>`, not Tag objects) + PaymentMethod enum — `.../domain/Expense.java`
- [ ] T054 [expense-service] [REQ-EXP-001] ExpenseService `POST /expenses` (amount>0, CategoryLookupPort validate, ownership) — `.../service/ExpenseService.java`
- [ ] T055 [expense-service] [REQ-EXP-003] `GET /expenses` paginated + filters (date/category/payment/tag/goal) + sort — `.../controller/ExpenseController.java`
- [ ] T056 [expense-service] [REQ-EXP-006] `GET/PUT/DELETE /expenses/{id}` (403-never-404 ownership) — `.../controller/ExpenseController.java`
- [ ] T057 [expense-service] [CQ-8] Emit `ExpenseCreated/Updated/DeletedEvent` via transactional outbox — `.../service/ExpenseService.java`
- [ ] T058 [expense-service] [Doc 08 §4.4] ContributionEventsPort — emit amount-adjusted/linked/unlinked on goal-link change — `.../port/ContributionEventsPort.java`
- [ ] T059 [expense-service] [Doc 08 §6.1] SpendingFeedPort — publish expense events for budget consumption — `.../port/SpendingFeedPort.java`
- [ ] T060 [expense-service] [SEC-5] ReceiptService `POST /receipt` — magic-byte sniff (jpeg/png/webp), ≤5 MB, **EXIF strip**, server key, 1:1 (409) — `.../service/ReceiptService.java`
- [ ] T061 [expense-service] [Doc 10 §5.3] `GET/DELETE /receipt` — secure headers (Content-Disposition, nosniff), MinIO stream — `.../controller/ReceiptController.java`
- [ ] T062 [expense-service] [REQ-TAG-002] TagManagementService CRUD + detach-on-delete — `.../service/TagManagementService.java`
- [ ] T063 [expense-service] [REQ-REC-001] RecurringExpenseService CRUD with `scope=THIS|THIS_AND_FUTURE` — `.../service/RecurringExpenseService.java`
- [ ] T064 [expense-service] [REQ-REC-003] RecurringExpenseGenerator @Scheduled + `RecurringGenerationFailedEvent` (parked for Phase-2 Notification) — `.../service/RecurringExpenseGenerator.java`
- [ ] T065 [expense-service] [Doc 10 §5.5] ExpenseImportService `POST /import` — ≤10 MB/10k rows, CSV-injection strip, per-row report, Idempotency-Key — `.../service/ExpenseImportService.java`
- [ ] T066 [expense-service] [REQ-EXP-014] ExpenseExportService `GET /export` (streaming CSV) — `.../service/ExpenseExportService.java`
- [ ] T067 [expense-service] [AL-2] CategoryLookupPort HTTP adapter (domain interface + infra impl) — `.../port/CategoryLookupHttpAdapter.java`
- [ ] T068 [expense-service] [Doc 04 §3] Integration suite incl. receipt security rejections (pdf/oversize/magic-mismatch) — `.../test/.../ExpenseIT.java`

### Phase 3 — Advanced Domains (`savings-goal-service` & `budget-service`)

#### savings-goal-service (Partnership pattern: contributions backed by expenses)

- [ ] T069 [savings-goal-service] [REQ-GOAL-001] Flyway V1 — `savings_goals` (status CHECK, total_contributed) — `.../db/migration/V1__create_savings_goals.sql`
- [ ] T070 [savings-goal-service] [SG-INV-4] Flyway V2 — `contribution_entries` (unique goal+expense) — `.../db/migration/V2__create_contribution_entries.sql`
- [ ] T071 [savings-goal-service] [REQ-GOAL-001] SavingsGoal entity + GoalStatus enum + ContributionEntry — `.../domain/SavingsGoal.java`
- [ ] T072 [savings-goal-service] [REQ-GOAL-001] Goal CRUD `GET/POST /savings-goals`, `GET/PUT/DELETE /{id}` + `?status=` — `.../controller/SavingsGoalController.java`
- [ ] T073 [savings-goal-service] [AL-2] ContributionPort — instruct expense-service to create backing Expense — `.../port/ContributionPort.java`
- [ ] T074 [savings-goal-service] [REQ-GOAL-004] ContributionService `POST /{id}/contributions` (primary flow → backing expense → entry → recompute total) — `.../service/ContributionService.java`
- [ ] T075 [savings-goal-service] [REQ-GOAL-006] `GET /{id}/contributions` history (paginated) — `.../controller/ContributionController.java`
- [ ] T076 [savings-goal-service] [REQ-GOAL-005] ExpenseEventConsumer — secondary flow (`ExpenseLinkedToSavingsGoalEvent` → entry) — `.../consumer/ExpenseEventConsumer.java`
- [ ] T077 [savings-goal-service] [REQ-GOAL-007] ContributionReconciliationService — react to amount-adjusted/deleted/unlinked (idempotent on eventId) — `.../service/ContributionReconciliationService.java`
- [ ] T078 [savings-goal-service] [SG-INV-6] Auto-complete on total≥target + emit `SavingsGoalCompletedEvent` (parked for Phase-2 Notification) — `.../service/GoalLifecycleService.java`
- [ ] T079 [savings-goal-service] [REQ-GOAL-012] `PATCH /{id}/status` state machine (illegal → 409) — `.../service/GoalLifecycleService.java`
- [ ] T080 [savings-goal-service] [REQ-GOAL-009] GoalProjectionService (avg rate; exclude PAUSED) — `.../service/GoalProjectionService.java`
- [ ] T081 [savings-goal-service] [REQ-GOAL-003] Emit `SavingsGoalDeletedEvent` (expense-service detaches) — `.../service/GoalLifecycleService.java`
- [ ] T082 [savings-goal-service] [Doc 04 §4] Integration suite: contribution loop, reconciliation on edit/delete, auto-complete once — `.../test/.../SavingsGoalIT.java`

#### budget-service (threshold detection + idempotent rollover ledgers)

- [ ] T083 [budget-service] [REQ-BUD-001] Flyway V1 — `budgets` (scope CHECK, partial active index) — `.../db/migration/V1__create_budgets.sql`
- [ ] T084 [budget-service] [BUD-INV-5] Flyway V2 — `budget_period_ledgers` (fired_eighty_percent, fired_exceeded, unique window) — `.../db/migration/V2__create_budget_period_ledgers.sql`
- [ ] T085 [budget-service] [REQ-BUD-001] Budget entity + ledger entity + Scope/PeriodType enums — `.../domain/Budget.java`
- [ ] T086 [budget-service] [REQ-BUD-001] BudgetAuthoringService CRUD + CategoryLookupPort for CATEGORY scope — `.../service/BudgetAuthoringService.java`
- [ ] T087 [budget-service] [REQ-BUD-002] `PATCH /{id}/activation` (deactivated never alerts — BUD-INV-7) — `.../controller/BudgetController.java`
- [ ] T088 [budget-service] [REQ-BUD-003] `PATCH /{id}/rollover` toggle — `.../controller/BudgetController.java`
- [ ] T089 [budget-service] [REQ-BUD-005] ExpenseEventConsumer (SpendingFeedPort) — idempotent recompute of `spent` — `.../consumer/ExpenseEventConsumer.java`
- [ ] T090 [budget-service] [BUD-INV-5] BudgetEvaluationService — fire 80% / exceeded **once per period per threshold** via flags + emit events (parked for Phase-2 Notification) — `.../service/BudgetEvaluationService.java`
- [ ] T091 [budget-service] [BUD-INV-8] BudgetRolloverService + scheduler — archive ledger, open new, carry-in if enabled (idempotent) — `.../service/BudgetRolloverService.java`
- [ ] T092 [budget-service] [REQ-BUD-007] BudgetStatusService `GET /{id}` derived fields (spent/remaining/percentUsed/firedThresholds/carriedIn) — `.../service/BudgetStatusService.java`
- [ ] T093 [budget-service] [Doc 04 §5] Integration suite: repeated events fire one alert, deactivated fires none, rollover boundary — `.../test/.../BudgetIT.java`

### Phase 4 — Event-Driven Infrastructure (Transactional Outbox)

> Hardens the publish/consume backbone the active services depend on. Notification consumers are out of scope.

- [ ] T094 [All services] [CQ-8] Per-service `outbox` table migration (Vn) + JPA mapping — `.../db/migration/Vn__create_outbox.sql`
- [ ] T095 [All services] [CQ-8] OutboxWriter — write event in **same @Transactional** as state change — `.../outbox/OutboxWriter.java`
- [ ] T096 [All services] [Doc 08 §1.3] OutboxRelayScheduler — poll pending → publish to broker → mark sent — `.../outbox/OutboxRelayScheduler.java`
- [ ] T097 [All consumers] [Doc 08 §1.3] `processed_events` table + idempotent-consume guard (dedup on eventId) — `.../consumer/ProcessedEventGuard.java`
- [ ] T098 [Repo] [Doc 08 §1.2] Standard event envelope (eventId/type/version/occurredAt/producer/userId/traceId/payload) in shared-kernel — `shared-kernel/.../outbox/EventEnvelope.java`
- [ ] T099 [Repo] [Doc 08] Event-flow integration test: expense create → budget ledger update + goal reconciliation across real Kafka — `.../test/.../EventFlowIT.java`

### Phase 5 — Frontend Integration

- [ ] T100 [frontend] [P2/FE-3] Vite + React 18 + TS strict (`"strict": true`, no `any`) scaffold — `frontend/tsconfig.json`
- [ ] T101 [frontend] [FE-6] apiConfig — env-based base URLs (no hardcoded URLs) — `frontend/src/lib/apiConfig.ts`
- [ ] T102 [frontend] [FE-1/FE-2] Single axiosClient + single-flight refresh interceptor (queue→refresh→replay) — `frontend/src/lib/axiosClient.ts`
- [ ] T103 [frontend] [FE-1] Auth store (in-memory access token) + ProtectedRoute guard — `frontend/src/features/auth/authStore.ts`
- [ ] T104 [frontend] [Doc 07 §2] Auth pages: login/register/verify/forgot/reset — `frontend/src/features/auth/`
- [ ] T105 [frontend] [FE-4] Shared LoadingState/ErrorState/EmptyState + PaginatedTable + MoneyDisplay/DateDisplay (en-IN) — `frontend/src/components/`
- [ ] T106 [frontend] [REQ-CAT] Categories feature (list/form, default vs custom) — `frontend/src/features/categories/`
- [ ] T107 [frontend] [REQ-EXP] Expenses feature: list+filters+sort+pagination, form, receipts (client pre-validate), tags, recurring, import/export — `frontend/src/features/expenses/`
- [ ] T108 [frontend] [REQ-GOAL] Savings Goals: list (active/completed), detail+progress+projection+history, contribution form — `frontend/src/features/savings-goals/`
- [ ] T109 [frontend] [REQ-BUD] Budgets: list+status cards, form, activation/rollover toggles — `frontend/src/features/budgets/`
- [ ] T110 [frontend] [REQ-A11Y/RWD] Accessibility (ARIA/keyboard/contrast) + responsive breakpoints pass — `frontend/src/`
- [ ] T111 [frontend] [REQ-TEST] Vitest + RTL + MSW: refresh-interceptor test, loading/error/empty per data view — `frontend/src/**/__tests__/`

---

## 3. Definition of Done (DoD) & Review Gates

> A phase's gate must be **fully green** before the agent proceeds. Each individual task must also
> satisfy the 3-Commit Loop: RED commit shows the test failing for the right reason; GREEN commit
> passes it; REFACTOR commit adds MDC tracing + DTO mapping + trace-map with no behavior change.

### Universal per-task gate (applies to every T###)

- [ ] 3 commits present in order: `test(RED) → feat(GREEN) → refactor(REFACTOR)`
- [ ] No `null` returned from any service method (`Optional<T>` only) — CQ-2
- [ ] No JPA entity on any controller signature (DTO-only) — AL-4
- [ ] Business logic only in `service/` (controllers thin, repos data-only) — CQ-1
- [ ] Every write method `@Transactional`; outbox write in the same tx — CQ-8
- [ ] `traceId` present on every log line; **0 PII** (email/name/amount/token) in logs or error envelope — CQ-12/13
- [ ] **0 magic literals** (enums/constants only) — DB-7/CQ
- [ ] No hardcoded secrets (env only) — SEC-6

### Phase 0 gate — Foundation

- [ ] `docker-compose up` → all infra healthy; CI pipeline green end-to-end
- [ ] shared-kernel unit tests green (JwtService, PiiMasker, MoneyDto); coverage on security utils
- [ ] GlobalExceptionHandler returns uniform envelope for 400/401/403/404/409/429 (contract test)

### Phase 1 gate — Identity & Access

- [ ] All Doc 04 §2 BDD scenarios pass as Testcontainers integration tests
- [ ] BCrypt cost ≥ 12 verified; no plaintext password in DB or logs (asserted)
- [ ] Refresh rotation: old token → 401; reused revoked token → **entire family revoked** (asserted)
- [ ] Every `/{id}`/`/me` path: foreign access → **403 never 404**; list endpoints isolate by user
- [ ] Rate limit fires `429 + Retry-After`; OpenAPI matches implementation (contract diff clean)

### Phase 2 gate — Core Domains

- [ ] category & expense Testcontainers suites green (incl. cross-service CategoryLookupPort validation)
- [ ] Default categories seeded on first boot; DEFAULT edit/delete → 403; in-use delete → 409
- [ ] Receipt: pdf/oversize(>5 MB)/extension-vs-magic-byte mismatch → 400; **EXIF stripped** on retrieval (asserted)
- [ ] CSV import: per-row report correct; injection neutralized; `Idempotency-Key` dedup verified
- [ ] Expense events visible on broker; money is `NUMERIC(19,4)`/`BigDecimal` end-to-end (0 float)

### Phase 3 gate — Advanced Domains

- [ ] savings-goal & budget Testcontainers suites green
- [ ] Contribution always backed by exactly one Expense; one ContributionEntry per backing expense (unique constraint proven)
- [ ] Total reconciles on backing-expense edit/delete/unlink; auto-complete fires **exactly once**
- [ ] Illegal goal status transition → 409
- [ ] Budget threshold fires **once per period per threshold** under repeated events; deactivated budget fires none; rollover carries unspent only when enabled

### Phase 4 gate — Event Infrastructure

- [ ] Outbox write + state change atomic (rollback test: no event leaks on failed tx)
- [ ] Consumers idempotent on `eventId` (duplicate delivery → single effect)
- [ ] Cross-service event-flow IT green against real Kafka (expense → budget + goal)
- [ ] No synchronous cross-service DB access anywhere (ArchUnit rule green) — AL-1

### Phase 5 gate — Frontend

- [ ] `tsc --noEmit` strict clean (0 `any`); lint clean
- [ ] Exactly one Axios instance; no raw `fetch`; no hardcoded API URLs (lint/grep gate) — FE-1/FE-6
- [ ] Transparent refresh proven via MSW (401 → single refresh → replay)
- [ ] Every data view renders Loading/Error/Empty states (RTL tests) — FE-4
- [ ] Accessibility audit (ARIA/keyboard/contrast) and responsive breakpoints pass

### Final release gate (before declaring Phase-1 product done)

- [ ] All 9 hard-stop violation classes (Doc 11 §4 / Plan Appendix A) absent (ArchUnit + lint + static scan)
- [ ] Full security suite (Plan Appendix B) green; MinIO buckets private (signed/proxied access only)
- [ ] OpenAPI contract diff clean for all 5 services; docs generated, not hand-written
- [ ] Income / Reporting / Notification contexts confirmed **not** implemented (deferred to Phase 2)

---

*End of `12-implementation-plan.md` — Daily Expense Application execution blueprint.*

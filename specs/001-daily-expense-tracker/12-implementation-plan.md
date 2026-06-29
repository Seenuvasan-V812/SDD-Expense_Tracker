# Tasks: Daily Expense Application (Phase 1) — AI-Agent Execution Plan

| Field | Value |
|-------|-------|
| **Document** | `12-implementation-plan.md` — the wired SpecKit **TASKS** artifact (`common.ps1` → `TASKS`) |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Governing Authority** | [Engineering Constitution v1.1.2](../../.specify/memory/constitution.md) + [Doc 11 Agent Pack](./11-agent-instruction-pack.md) |
| **Status** | Ready for `/speckit-analyze` → `/speckit-implement` |
| **Regenerated** | 2026-06-27 by `/speckit-tasks` (reconciled with prior T001–T111) |

> **This file is self-sufficient.** `/speckit-implement` reads only this file + `plan.md` + `data-model.md`
> + `contracts/` + `research.md` + `quickstart.md` + the constitution — it does **not** open
> `13-task-breakdown.md` or `14-test-strategy.md`. Therefore every task below carries its **RED test
> artifact** and a **binary acceptance criterion** folded in from `13`/`14`. Names use `02-glossary.md`
> verbatim; anti-terms are prohibited.

**Input**: design documents from `specs/001-daily-expense-tracker/` (plan.md, data-model.md, contracts/, research.md, quickstart.md)
**Tests**: REQUIRED (constitution P6 + 3-Commit Loop + SC-05). Each implementation task is one **RED → GREEN → REFACTOR** loop; the RED test is named and written first.

---

## Mandate to the AI coding agent

This is your exact execution order. Do **not** reorder, skip, or batch tasks. Each task `T###` is one unit
of the **3-Commit Loop**: **RED** (write the named failing Testcontainers/Mockito/Vitest test first) →
**GREEN** (minimal implementation to pass) → **REFACTOR** (MDC tracing, DTO mapping, cleanup — no behaviour
change). A task is done only when its phase Review Gate (§3) is green. Every non-negotiable law
(P1–P7, AL-1…AL-5, API-1…API-7, SEC-1…SEC-6, CQ-1…CQ-14, FE-1…FE-7, DB-1…DB-9) is binding at all times.

**Active scope (Phase 1):** `user-service`, `category-service`, `expense-service`, `savings-goal-service`,
`budget-service`.
**Deferred to Phase 2 (spec.md §7 — DO NOT BUILD):** Income, Reporting/Dashboard/Reports, **Notification
consumer**/Notification Center. Active services still **write** notification/reporting events to the
transactional outbox; there is **no consumer** in scope.

### Label legend

- **Story labels** map to `spec.md` Section 1 user stories:
  `US1`=US-01 record Expense · `US2`=US-02 Recurring Expense · `US3`=US-03 Savings Goals & Contributions ·
  `US4`=US-04 Budgets & Alerts · `US5`=US-05 Receipts · `US6`=US-06 CSV import · `US7`=US-07 CSV export.
- **Foundational / cross-cutting** tasks (shared-kernel, user-service, category-service, outbox infra)
  carry their governing **Constitution law** or **REQ-*** id instead of a `US` label.
- **`[P]`** = parallelizable (disjoint files, all Depends-On satisfied).
- Per-task lines: **RED** = the failing test to write first (from `14-test-strategy.md`); **AC** = binary
  acceptance criterion (from `13-task-breakdown.md`).

---

## 1. Architectural & Repository Scaffolding

### 1.1 Monorepo folder structure

```text
daily-expense-app/
├── pom.xml                          # parent aggregator POM (Java 21, Spring Boot 3.x BOM)
├── docker-compose.yml               # postgres×5, minio, kafka, zookeeper, mailhog
├── .github/workflows/ci.yml         # build · tsc · lint · unit · integration · contract-diff
├── shared-kernel/                   # versioned internal library — NO domain logic, NO repositories
│   └── src/main/java/com/dailyexpense/shared/
│       ├── api/          # PageResponse (API-2), ErrorResponse/ApiError (API-3)
│       ├── exception/    # GlobalExceptionHandler, Forbidden/NotFound/Conflict exceptions
│       ├── money/        # MoneyDto (BigDecimal + currency, DB-5)
│       ├── security/     # JwtService (HS256), JwtAuthenticationFilter, CallerContext (AL-5)
│       ├── observability/# TraceIdFilter, RequestLoggingFilter, PiiMasker (CQ-11/12/13)
│       └── outbox/       # OutboxEntry, OutboxPublisher, EventEnvelope (CQ-8, Doc 08 §1.2)
├── services/
│   ├── user-service/            # identity_db; auth, tokens, profile, data export
│   ├── category-service/        # category_db; default seed + custom CRUD; CategoryLookupPort
│   ├── expense-service/         # expense_db; expenses, receipts(+MinIO), tags, recurring, CSV
│   ├── savings-goal-service/    # savings_goal_db; goals, contributions, reconcile consumer
│   └── budget-service/          # budget_db; budgets, ledgers, spending consumer, rollover
│       # each: controller/ service/ repository/ domain/ dto/ port/ scheduler|consumer|storage/
│       #       resources/db/migration/  test/ (Mockito unit + Testcontainers integration)
└── frontend/
    └── src/
        ├── lib/         # axiosClient (single instance, FE-1/2), apiConfig (env, FE-6)
        ├── features/    # auth, categories, expenses, savings-goals, budgets
        ├── components/  # LoadingState/ErrorState/EmptyState (FE-4), PaginatedTable, Money/DateDisplay (en-IN)
        └── types/       # TS mirrors of API DTOs (strict, no any)
```

**Isolation rules baked into scaffolding:** each service owns its **own** `db/migration` and datasource;
`shared-kernel` has **zero** domain logic and **zero** repositories (no cross-context coupling — AL-1/AL-3);
cross-context reads go through `port/` adapters only (AL-2).

### 1.2 Database migration strategy

- **Flyway**, one isolated history **per service database**; naming `V<n>__<verb>_<noun>.sql`.
- Every PK `id UUID` (app-generated); every table `created_at`/`updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
  maintained by a per-DB `set_updated_at()` trigger (CQ-9); money `NUMERIC(19,4)` + `currency` CHECK `='INR'`
  (DB-5); enums `VARCHAR + CHECK` UPPER_SNAKE (DB-7); every filter/join column indexed (DB-4); cross-service
  refs are bare `UUID`, **no FK** (DB-2/AL-1). Migrations additive; `validate-on-migrate=true`.

---

## 2. Task List (dependency-ordered; T001–T111 preserved)

### Phase 0 — Setup & Foundational: Shared Kernel & Infra  *(no US — blocks all stories)*

- [x] T001 [Setup] Initialize parent Maven aggregator POM (Java 21, Spring Boot 3.x BOM, JUnit5, Mockito, Testcontainers) — `pom.xml`
  - RED: `mvn verify` on parent fails until modules resolve · AC: `shared-kernel` resolves in all 5 service POMs; Java 21 + Spring Boot 3.x BOM declared.
- [x] T002 [Setup] Author Docker Compose (postgres×5 isolated, minio, kafka+zookeeper, mailhog) — `docker-compose.yml`
  - RED: compose health check fails pre-impl · AC: `docker compose up` healthy; 5 distinct PostgreSQL DBs (`identity_db`,`category_db`,`expense_db`,`savings_goal_db`,`budget_db`), no shared schema.
- [x] T003 [Setup] CI pipeline: build → `tsc` → lint → ArchUnit → unit → Testcontainers → OpenAPI contract-diff — `.github/workflows/ci.yml`
  - RED: pipeline red on scaffold · AC: all stages green on scaffold commit; contract-diff gate present; `ArchitectureRulesTest` scaffolded in each service's `test/` (covers G-04: no entity in controller payload, no cross-service package import, Optional-only service returns, BigDecimal money, no @Transactional-missing on writes, no hardcoded secrets); ArchUnit runs before Testcontainers in CI.
- [x] T004 [P] [API-2] PageResponse<T> uniform pagination envelope — `shared-kernel/src/main/java/com/dailyexpense/shared/api/PageResponse.java`
  - RED: `PageResponseTest` · AC: serializes exactly `content,page,size,totalElements,totalPages`; no extra keys; generic preserved.
- [x] T005 [P] [API-3] ErrorResponse + ApiError uniform error envelope (no PII — CQ-13) — `shared-kernel/src/main/java/com/dailyexpense/shared/api/ErrorResponse.java`
  - RED: `ErrorResponseTest` · AC: serializes exactly `timestamp,status,error,message,path,traceId`; `message` carries no email/amount/token.
- [x] T006 [API-3] GlobalExceptionHandler @ControllerAdvice (400/401/403/404/409/429) — `shared-kernel/src/main/java/com/dailyexpense/shared/exception/GlobalExceptionHandler.java`
  - RED: `GlobalExceptionHandlerTest` (Mockito per mapped exception) · AC: each exception → correct status; body is `ErrorResponse`; no PII. Depends: T005, T007.
- [x] T007 [P] [SEC-3] Domain exceptions: ForbiddenOwnership/ResourceNotFound/BusinessConflict — `shared-kernel/src/main/java/com/dailyexpense/shared/exception/`
  - RED: handler test for 403 path · AC: throwing `ForbiddenOwnershipException` → HTTP 403 (never 404).
- [x] T008 [P] [DB-5] MoneyDto (BigDecimal scale-2 + currency) — `shared-kernel/src/main/java/com/dailyexpense/shared/money/MoneyDto.java`
  - RED: `MoneyDtoTest` · AC: `{amount:"100.50",currency:"INR"}` scale-2 string; `double` ctor rejected; non-INR rejected.
- [x] T009 [SEC-2] JwtService — HS256 sign/verify, claims sub/iat/exp/jti/typ, 15-min access — `shared-kernel/src/main/java/com/dailyexpense/shared/security/JwtService.java`
  - RED: `JwtServiceTest` · AC: round-trip verifies (`sub`=UUID not email); expired → reject; `typ:refresh` rejected by access validator; `JWT_SECRET` from env (SEC-6).
- [x] T010 [AL-5] JwtAuthenticationFilter — `sub`→CallerContext + MDC userId — `shared-kernel/src/main/java/com/dailyexpense/shared/security/JwtAuthenticationFilter.java`
  - RED: filter test · AC: valid JWT → principal=userId UUID, MDC has userId; invalid → 401; email/name never in MDC. Depends: T009.
- [x] T011 [P] [CQ-12] TraceIdFilter — generate/propagate `traceId` into MDC per request — `shared-kernel/src/main/java/com/dailyexpense/shared/observability/TraceIdFilter.java`
  - RED: `TraceIdFilterTest` · AC: every request has distinct `traceId` in MDC; cleared after response (no bleed).
- [x] T012 [CQ-11] RequestLoggingFilter — method/path/status/latency/traceId — `shared-kernel/src/main/java/com/dailyexpense/shared/observability/RequestLoggingFilter.java`
  - RED: `RequestLoggingFilterIT`-style log capture · AC: log line has 5 fields; no email/amount/password/Authorization. Depends: T011.
- [x] T013 [P] [CQ-13] PiiMasker — mask email/name; omit amounts/tokens — `shared-kernel/src/main/java/com/dailyexpense/shared/observability/PiiMasker.java`
  - RED: `PiiMaskerTest` · AC: email/name masked; token→`***REDACTED***`; **no `maskAmount` method exists**.
- [x] T014 [CQ-8] OutboxEntry + OutboxPublisher interface (transactional outbox contract) — `shared-kernel/src/main/java/com/dailyexpense/shared/outbox/`
  - RED: `OutboxEntryTest` · AC: maps `id,event_id,aggregate_type,aggregate_id,event_type,payload(JSONB),published,created_at,published_at`; `publish(EventEnvelope)` declared.
- [x] T015 [P] [P6] Shared-kernel unit suite (RED→GREEN→REFACTOR) for JwtService/PiiMasker/MoneyDto — `shared-kernel/src/test/java/...`
  - RED: failing suite first · AC: `mvn test -pl shared-kernel` 100%; ≥80% coverage on `security/`; Phase 0 gate green. Depends: T009, T013, T008.

### Phase 1 — Foundational: Identity & Access (`user-service`)  *(no US — prerequisite for all stories; REQ-USR)*

- [x] T016 [REQ-USR-001] Flyway V1 — `users` + `set_updated_at()` trigger (status/locale/timezone/weekly_digest) — `services/user-service/src/main/resources/db/migration/V1__create_users.sql`
  - RED: migration apply test · AC: `id UUID PK`, `status CHECK(INACTIVE_UNVERIFIED,ACTIVE,DELETED)`, `password_hash VARCHAR(72)`, audit cols + trigger, `uq_users_email`. Depends: T001,T002.
- [x] T017 [SEC-2] Flyway V2 — `refresh_tokens` (`family_id` NOT NULL + indexes) — `services/user-service/src/main/resources/db/migration/V2__create_refresh_tokens.sql`
  - RED: migration test · AC: `family_id UUID NOT NULL`, `token_hash UNIQUE`, idx family/expires/user. Depends: T016.
- [x] T018 [P] [REQ-USR-004] Flyway V3 — `email_verifications` — `services/user-service/src/main/resources/db/migration/V3__create_email_verifications.sql`
  - RED: migration test · AC: `token_hash UNIQUE`, `consumed_at?`, FK→users CASCADE. Depends: T016.
- [x] T019 [P] [REQ-USR-007] Flyway V4 — `password_reset_tokens` — `services/user-service/src/main/resources/db/migration/V4__create_password_reset_tokens.sql`
  - RED: migration test · AC: `token_hash UNIQUE`, `consumed_at?`, FK→users CASCADE. Depends: T016.
- [x] T020 [P] [REQ-USR-011] Flyway V5 — `data_exports` — `services/user-service/src/main/resources/db/migration/V5__create_data_exports.sql`
  - RED: migration test · AC: `status CHECK(REQUESTED,READY,FAILED)`, `download_ref?`, FK→users CASCADE. Depends: T016.
- [x] T021 [REQ-USR-001] User JPA entity + UserStatus enum — `services/user-service/src/main/java/com/dailyexpense/user/domain/User.java`
  - RED: serialization-boundary test · AC: enum has 3 values; entity never JSON-serialized (AL-4); absent from any `*Response`. Depends: T016.
- [x] T022 [CQ-2] UserRepository (Spring Data; Optional returns) — `services/user-service/src/main/java/com/dailyexpense/user/repository/UserRepository.java`
  - RED: `UserRepositoryTest` · AC: `findByEmail`→`Optional<User>`; empty for absent. Depends: T021.
- [x] T023 [REQ-USR-003] RegistrationService (register; dup email → 409; INACTIVE_UNVERIFIED) — `services/user-service/src/main/java/com/dailyexpense/user/service/RegistrationService.java`
  - RED: `RegistrationServiceTest` (Mockito) · AC: dup→BusinessConflictException; new user INACTIVE_UNVERIFIED; BCrypt-hashed; no plaintext in logs. Depends: T022,T024.
- [x] T024 [P] [SEC-1] BCrypt password encoder config (cost ≥ 12) — `services/user-service/src/main/java/com/dailyexpense/user/security/PasswordEncoderConfig.java`
  - RED: `PasswordEncoderConfigTest` · AC: hash prefix `$2a$12$`; plaintext unrecoverable. Depends: T001.
- [x] T025 [REQ-USR-004] EmailVerificationService + `UserRegisteredEvent` via outbox — `services/user-service/src/main/java/com/dailyexpense/user/service/EmailVerificationService.java`
  - RED: atomicity IT · AC: `email_verifications` row + outbox row in SAME tx; rollback → both absent; `token_hash`=SHA-256(raw). Depends: T023,T014,T018.
- [x] T026 [REQ-USR-004] AuthController `POST /api/v1/auth/register`, `GET /api/v1/auth/verify-email` — `services/user-service/src/main/java/com/dailyexpense/user/controller/AuthController.java`
  - RED: `AuthFlowIT` register cases · AC: 201+`Location`; dup→409; verify activates→200. Depends: T025,T006.
- [x] T027 [REQ-USR-005] AuthenticationService `POST /auth/login` (refuse unverified; issue access+refresh) — `services/user-service/src/main/java/com/dailyexpense/user/service/AuthenticationService.java`
  - RED: `AuthFlowIT` login · AC: correct creds→AuthTokenResponse(`expiresInSec:900`); wrong/unverified→401 generic; no plaintext in logs. Depends: T022,T024,T009.
- [x] T028 [SEC-2] RefreshToken entity + RefreshTokenRepository (`findByTokenHash`/`findAllByFamilyId`, SHA-256) — `services/user-service/src/main/java/com/dailyexpense/user/domain/RefreshToken.java`
  - RED: repo test · AC: only SHA-256 hash stored; family lookup present. Depends: T017.
- [x] T029 [REQ-SEC-002] TokenRotationService `POST /auth/refresh` (rotate; reuse → family-wide revoke) — `services/user-service/src/main/java/com/dailyexpense/user/service/TokenRotationService.java`
  - RED: `AuthFlowIT`/`TokenRotationService` test · AC: valid→new pair + old revoked; revoked reuse→401 AND whole `family_id` revoked; expired→401. Depends: T027,T028.
- [x] T030 [REQ-USR-006] `POST /auth/logout` (revoke session refresh token) — `services/user-service/src/main/java/com/dailyexpense/user/controller/AuthController.java`
  - RED: logout IT · AC: 204; token `revoked_at` set; later refresh→401. Depends: T029.
- [x] T031 [REQ-USR-007] Password reset: forgot + reset + `PasswordResetRequestedEvent`; revoke all refresh — `services/user-service/src/main/java/com/dailyexpense/user/service/AccountLifecycleService.java`
  - RED: `PasswordResetIT` · AC: forgot→202 uniform (no enumeration); event in outbox; reset→204 + all refresh revoked; reuse token→400. Depends: T025,T019.
- [x] T032 [REQ-USR-008] UserController `GET/PUT /users/me` (profile + locale + weeklyDigest) — `services/user-service/src/main/java/com/dailyexpense/user/controller/UserController.java`
  - RED: `UserProfileIT` · AC: GET→200 no passwordHash; PUT→200; body `userId` ignored (identity from JWT, AL-5). Depends: T021,T010.
- [x] T033 [REQ-USR-009] `PATCH /users/me/password` (verify current; re-hash; revoke all refresh) — `services/user-service/src/main/java/com/dailyexpense/user/controller/UserController.java`
  - RED: `UserProfileIT` change-pw · AC: correct current→204 + all refresh revoked; wrong→400; new hash `$2a$12$`. Depends: T032,T024.
- [x] T034 [REQ-USR-010] `DELETE /users/me` + `UserDeletedEvent` via outbox — `services/user-service/src/main/java/com/dailyexpense/user/service/AccountLifecycleService.java`
  - RED: `UserProfileIT` delete · AC: 204; status DELETED; all refresh revoked; event in same tx; rollback→both absent; later login→401. Depends: T032,T014.
- [x] T035 [REQ-USR-011] DataExportService `POST /users/me/data-export` (202) + signed `GET .../download` — `services/user-service/src/main/java/com/dailyexpense/user/service/DataExportService.java`
  - RED: `UserDataExportIT` export endpoints · AC: 202+exportId; status REQUESTED; download→200 for owner, 403 foreign; `download_ref` never logged; cross-service aggregation wired via T112 (UserDataPort). Depends: T032,T020.
- [x] T036 [Doc 05 §8] SecureNotificationDeliveryPort impl (resolve deliveryRef → one-time URL) — `services/user-service/src/main/java/com/dailyexpense/user/port/SecureNotificationDeliveryPort.java`
  - RED: port contract test · AC: valid deliveryRef→time-limited URL; no cross-service DB access (AL-1). Depends: T035.
- [x] T037 [SEC-4] Auth rate-limit filter (per-IP + per-account) → 429 + Retry-After — `services/user-service/src/main/java/com/dailyexpense/user/security/AuthRateLimitFilter.java`
  - RED: `AuthFlowIT` rate-limit loop · AC: N+1 login→429 + integer `Retry-After`; forgot-password uniform 202; failed login WARN + masked email. Depends: T010,T006.
- [x] T038 [Doc 10 §2.8] TokenCleanupScheduler (purge expired/revoked) — `services/user-service/src/main/java/com/dailyexpense/user/scheduler/TokenCleanupScheduler.java`
  - RED: scheduler test · AC: deletes expired AND revoked; active untouched; idempotent. Depends: T028.
- [x] T039 [P6] Auth integration suite (Testcontainers, full BDD) — `services/user-service/src/test/java/com/dailyexpense/user/AuthFlowIT.java`
  - RED: full `AuthFlowIT` + `PasswordResetIT` + `UserProfileIT` first · AC: register→verify→login→refresh→reuse(family revoke)→logout; BCrypt≥12; 403-never-404; no PII; Phase 1 gate green. Depends: T037,T034.

### Phase 2a — Foundational: Category (`category-service`)  *(no US — prerequisite for US1/US4; REQ-CAT)*

- [x] T040 [REQ-CAT-001] Flyway V1 — `categories` (+ trigger, constraints, partial savings index) — `services/category-service/src/main/resources/db/migration/V1__create_categories.sql`
  - RED: migration test · AC: `ck_categories_default_no_owner`, `uq_categories_owner_name`, partial `idx_categories_system_role WHERE system_role='SAVINGS'`. Depends: T001,T002.
- [x] T041 [REQ-CAT-001] Category entity + CategoryType/Origin/SystemRole enums — `services/category-service/src/main/java/com/dailyexpense/category/domain/Category.java`
  - RED: enum/serialization test · AC: type{EXPENSE,INCOME,BOTH}, origin{DEFAULT,CUSTOM}, role{NONE,SAVINGS}; not JSON-serializable (AL-4). Depends: T040.
- [x] T042 [REQ-CAT-001] DefaultCategorySeeder ApplicationRunner (11 defaults; Savings role) — `services/category-service/src/main/java/com/dailyexpense/category/initializer/DefaultCategorySeeder.java`
  - RED: seeder IT · AC: ≥11 defaults incl. Savings (`system_role='SAVINGS'`,`user_id=NULL`); idempotent re-seed. Depends: T041.
- [x] T043 [REQ-CAT-002] CategoryAuthoringService + `GET/POST/PUT /categories` (name unique per owner) — `services/category-service/src/main/java/com/dailyexpense/category/service/CategoryAuthoringService.java`
  - RED: `CategoryIT` create/edit · AC: POST→201+Location; dup name same owner→409; per-owner uniqueness only. Depends: T041,T007.
- [x] T044 [REQ-CAT-003] Block DEFAULT edit/delete; ownership on Custom — `services/category-service/src/main/java/com/dailyexpense/category/service/CategoryAuthoringService.java`
  - RED: `CategoryIT` protection · AC: DELETE default→409; PUT default→403; foreign custom PUT/DELETE→403 (not 404). Depends: T043.
- [x] T045 [REQ-CAT-005] CategoryDeletionGuard via CategoryUsagePort (in-use → 409) — `services/category-service/src/main/java/com/dailyexpense/category/service/CategoryDeletionGuard.java`
  - RED: guard unit + `CategoryIT` · AC: in-use→BusinessConflictException(409) via port (no cross-service SQL, AL-1). Depends: T043,T044.
- [x] T046 [AL-2] CategoryLookupPort internal endpoint (validate id+visibility+type) — `services/category-service/src/main/java/com/dailyexpense/category/port/CategoryLookupController.java`
  - RED: `CategoryLookupPort` contract test · AC: foreign custom invisible→fail; INCOME-type rejected for Expense use; DEFAULT visible to all. Depends: T043.
- [x] T047 [REQ-CAT-004] `GET /categories/{id}` + `?type=` filter — `services/category-service/src/main/java/com/dailyexpense/category/controller/CategoryController.java`
  - RED: `CategoryIT` filter · AC: `?type=EXPENSE`→EXPENSE/BOTH only; `{id}` owner/DEFAULT→200, foreign custom→403; `PageResponse` present. Depends: T043.
- [x] T048 [P6] category-service integration suite (Testcontainers) — `services/category-service/src/test/java/com/dailyexpense/category/CategoryIT.java`
  - RED: full `CategoryIT` first · AC: default protection, in-use 409, uniqueness, INCOME-rejection via port, foreign 403; Doc 04 §7 tags pass. Depends: T047,T045.

### Phase 2b — User Story 1/2/5/6/7: Expense / Transaction (`expense-service`)

- [x] T049 [US1] [REQ-EXP-001] Flyway V1 — `expenses` (+ trigger, indexes, composite user_date) — `services/expense-service/src/main/resources/db/migration/V1__create_expenses.sql`
  - RED: migration test · AC: `amount NUMERIC(19,4) CHECK>0`, `payment_method CHECK(6 values)`, `category_id`/`savings_goal_id` no-FK, `idx_expenses_user_date(user_id,expense_date DESC)`. Depends: T001,T002.
- [x] T050 [P] [US5] [EXP-INV-7] Flyway V2 — `receipts` (unique expense_id, size CHECK) — `services/expense-service/src/main/resources/db/migration/V2__create_receipts.sql`
  - RED: migration test · AC: `uq_receipts_expense_id`, `ck_receipts_size_max(≤5242880)`, `mime_type CHECK(jpeg/png/webp)`, FK→expenses CASCADE. Depends: T049.
- [x] T051 [P] [US1] [REQ-TAG-001] Flyway V3 — `tags` + `expense_tags` — `services/expense-service/src/main/resources/db/migration/V3__create_tags.sql`
  - RED: migration test · AC: `uq_tags_owner_name`, join PK(expense_id,tag_id) both CASCADE, `idx_expense_tags_tag_id`. Depends: T049.
- [x] T052 [P] [US2] [REQ-REC-001] Flyway V4 — `recurring_expenses` + `recurring_expense_tags` — `services/expense-service/src/main/resources/db/migration/V4__create_recurring_expenses.sql`
  - RED: migration test · AC: `frequency CHECK(4)`, `generated_count DEFAULT 0`, `idx_recurring_expenses_next_run_date`. Depends: T049.
- [x] T053 [US1] [REQ-EXP-001] Expense entity (`Set<TagId>`) + PaymentMethod enum — `services/expense-service/src/main/java/com/dailyexpense/expense/domain/Expense.java`
  - RED: entity test · AC: 6-value enum; holds `Set<UUID>` not `Set<Tag>` (INV-9); `savings_goal_id` plain UUID (no `@ManyToOne`); not JSON-serializable (AL-4). Depends: T049.
- [x] T054 [US1] [REQ-EXP-001] ExpenseService `POST /expenses` (amount>0, CategoryLookupPort, ownership) — `services/expense-service/src/main/java/com/dailyexpense/expense/service/ExpenseService.java`
  - RED: `ExpenseService` unit + `ExpenseCrudIT` · AC: valid→201+Location; amount≤0→400; missing field→400 w/ field; foreign category→403; userId from JWT; category validated via port; **EXP-INV-5**: `savingsGoalId` present with `categoryId` ≠ Savings Category→400 (category type mismatch); `savingsGoalId` absent→category unrestricted. Depends: T053,T046,T006.
- [x] T055 [US1] [REQ-EXP-003] `GET /expenses` paginated + filters + sort — `services/expense-service/src/main/java/com/dailyexpense/expense/controller/ExpenseController.java`
  - RED: `ExpenseFilterIT` · AC: `PageResponse` 5 keys; filters from/to/categoryId/paymentMethod/tagId/savingsGoalId narrow correctly; sort date/amount; cross-user isolation (DB-6). Depends: T054.
- [x] T056 [US1] [REQ-EXP-006] `GET/PUT/DELETE /expenses/{id}` (403-never-404) — `services/expense-service/src/main/java/com/dailyexpense/expense/controller/ExpenseController.java`
  - RED: `ExpenseCrudIT` ownership · AC: owner→200/204; foreign→403; missing→404; DELETE cascades Receipt; PUT updates incl. `savingsGoalId`. Depends: T054,T007.
- [x] T057 [US1] [CQ-8] Emit ExpenseCreated/Updated/Deleted via transactional outbox — `services/expense-service/src/main/java/com/dailyexpense/expense/service/ExpenseService.java`
  - RED: `OutboxAtomicityIT` · AC: outbox row in same tx per CUD; rollback→absent; payload has eventId/type/userId/traceId/amount/categoryId. Depends: T054,T056,T014.
- [x] T058 [US3] [Doc 08 §4.4] ContributionEventsPort — emit linked/unlinked/amount-adjusted on goal-link change — `services/expense-service/src/main/java/com/dailyexpense/expense/port/ContributionEventsPort.java`
  - RED: port contract test · AC: add/change/remove `savingsGoalId` → correct event in outbox same tx; no `savings_goal_db` SQL (AL-1). Depends: T057.
- [x] T059 [US4] [Doc 08 §6.1] SpendingFeedPort — publish expense events for budget consumption — `services/expense-service/src/main/java/com/dailyexpense/expense/port/SpendingFeedPort.java`
  - RED: port contract test · AC: `ExpenseCreatedEvent` payload has categoryId/amount/userId; no `budget_db` SQL (AL-1). Depends: T057.
- [x] T060 [US5] [SEC-5] ReceiptService `POST /expenses/{id}/receipt` (magic-byte, EXIF strip, ≤5MB, pixel guard, 1:1) — `services/expense-service/src/main/java/com/dailyexpense/expense/service/ReceiptService.java`
  - RED: `ReceiptIT` · AC: jpeg/png/webp→201; pdf/gif/>5MB→400; 5MB exact→200; EXIF 0 segments on stored bytes; key=`receipts/{userId}/{uuid}`; replace 1:1 (EXP-INV-7); foreign→403. Depends: T056,T050.
- [x] T061 [US5] [Doc 10 §5.3] `GET/DELETE /expenses/{id}/receipt` (secure headers, MinIO stream) — `services/expense-service/src/main/java/com/dailyexpense/expense/controller/ReceiptController.java`
  - RED: `ReceiptIT` serve/delete · AC: GET streams w/ `Content-Disposition:inline` + `nosniff`; foreign→403; DELETE→204 removes object+row, Expense retained. Depends: T060.
- [x] T062 [US1] [REQ-TAG-002] TagManagementService CRUD + detach-on-delete — `services/expense-service/src/main/java/com/dailyexpense/expense/service/TagManagementService.java`
  - RED: `TagIT` · AC: POST→201; dup name→409; DELETE→204 detaches from Expenses (Expenses kept); foreign→403; `?tagId=` filter works. Depends: T051,T007.
- [x] T063 [US2] [REQ-REC-001] RecurringExpenseService CRUD with `scope=THIS|THIS_AND_FUTURE` — `services/expense-service/src/main/java/com/dailyexpense/expense/service/RecurringExpenseService.java`
  - RED: `RecurringExpenseIT` · AC: POST→201+Location; `THIS` edits only that Occurrence; `THIS_AND_FUTURE` sets template `end_date`=day-before + new forward template (REC-INV-2). Depends: T052,T053.
- [x] T064 [US2] [REQ-REC-003] RecurringExpenseGenerator @Scheduled + `RecurringGenerationFailedEvent` — `services/expense-service/src/main/java/com/dailyexpense/expense/service/RecurringExpenseGenerator.java`
  - RED: `RecurringExpenseIT` scheduler · AC: due templates create Occurrence w/ `recurring_expense_id`, copy tags, advance `next_run_date`, increment count; failure→event (parked); idempotent on same date. Depends: T063,T057.
- [x] T065 [US6] [REQ-EXP-012] ExpenseImportService `POST /expenses/import` (≤10MB/10k, injection strip, Idempotency-Key) — `services/expense-service/src/main/java/com/dailyexpense/expense/service/ExpenseImportService.java`
  - RED: `CsvImportIT` · AC: >10MB/>10k/non-csv→400; strip `= + - @ \t \r`; Idempotency-Key dedup; per-row report; goal match own goals only; unmatched→SUCCEEDED_WITH_WARNING. Depends: T054.
- [x] T066 [US7] [REQ-EXP-014] ExpenseExportService `GET /expenses/export` (streaming CSV) — `services/expense-service/src/main/java/com/dailyexpense/expense/service/ExpenseExportService.java`
  - RED: `CsvExportIT` · AC: `text/csv` streamed (no full in-memory load, CQ-10); cells injection-sanitized; only caller's rows in range. Depends: T055.
- [x] T067 [US1] [AL-2] CategoryLookupPort HTTP adapter (expense side) — `services/expense-service/src/main/java/com/dailyexpense/expense/port/CategoryLookupHttpAdapter.java`
  - RED: adapter unit · AC: calls category-service endpoint; not-found→ForbiddenOwnershipException; no `category_db` SQL (AL-1). Depends: T046,T054.
- [x] T068 [P6] expense-service integration suite (Testcontainers) — `services/expense-service/src/test/java/com/dailyexpense/expense/ExpenseIT.java`
  - RED: suites first (`ExpenseCrudIT`,`ExpenseFilterIT`,`ReceiptIT`,`TagIT`,`RecurringExpenseIT`,`CsvImportIT`,`CsvExportIT`) · AC: receipt rejections→400, EXIF 0 segments, CSV injection neutralized, Idempotency dedup, foreign→403, tag detach, recurring split; Doc 04 §3/§8/§9/§10 tags pass; Phase 2 gate green. Depends: T066,T061.

### Phase 3a — User Story 3: Savings Goal (`savings-goal-service`)

- [x] T069 [US3] [REQ-GOAL-001] Flyway V1 — `savings_goals` (status CHECK, total_contributed) — `services/savings-goal-service/src/main/resources/db/migration/V3__create_savings_goals.sql`
  - RED: migration test · AC: `status CHECK(4)`, `total_contributed CHECK≥0`, `target_amount CHECK>0`, `idx_savings_goals_user_status`. Depends: T001,T002. Note: V3 (V1=outbox/T094, V2=processed_events/T097).
- [x] T070 [US3] [SG-INV-4] Flyway V2 — `contribution_entries` (unique goal+expense) — `services/savings-goal-service/src/main/resources/db/migration/V4__create_contribution_entries.sql`
  - RED: migration test · AC: `uq_contribution_entries_goal_expense`, `expense_id` no-FK, `source CHECK(GOAL_SCREEN,LINKED_EXPENSE)`, FK→goals CASCADE. Depends: T069. Note: V4 (follows T094/T097).
- [x] T071 [US3] [REQ-GOAL-001] SavingsGoal entity + GoalStatus enum + ContributionEntry — `services/savings-goal-service/src/main/java/com/dailyexpense/savingsgoal/domain/SavingsGoal.java`
  - RED: entity test · AC: status enum 4 values; `expense_id` plain UUID (no `@ManyToOne`); source enum; not JSON-serializable (AL-4). Depends: T069,T070.
- [x] T072 [US3] [REQ-GOAL-001] Goal CRUD `GET/POST /savings-goals`, `GET/PUT/DELETE /{id}` + `?status=` — `services/savings-goal-service/src/main/java/com/dailyexpense/savingsgoal/controller/SavingsGoalController.java`
  - RED: `SavingsGoalIT` CRUD · AC: POST→201 status ACTIVE total 0; DELETE→204 detaches Expenses + `SavingsGoalDeletedEvent`; status split correct; foreign→403. Depends: T071,T007.
- [x] T073 [US3] [AL-2] ContributionPort — instruct expense-service to create backing Expense — `services/savings-goal-service/src/main/java/com/dailyexpense/savingsgoal/port/ContributionPort.java`
  - RED: port contract test · AC: calls expense-service with `categoryId`=Savings Category; no `expense_db` SQL (AL-1). Depends: T072,T046.
- [x] T074 [US3] [REQ-GOAL-004] ContributionService `POST /{id}/contributions` (primary flow) — `services/savings-goal-service/src/main/java/com/dailyexpense/savingsgoal/service/ContributionService.java`
  - RED: `SavingsGoalIT` contribution · AC: 201; backing Expense via port under Savings Category; entry `source=GOAL_SCREEN`; total recomputed; `uq` prevents dup per expense_id; appears in goal history + expense list. Depends: T073,T071.
- [x] T075 [US3] [REQ-GOAL-006] `GET /{id}/contributions` history (paginated) — `services/savings-goal-service/src/main/java/com/dailyexpense/savingsgoal/controller/SavingsGoalController.java`
  - RED: `SavingsGoalIT` history · AC: `PageResponse`; entries expose amount/currency/date/source; foreign→403; caller-only. Depends: T074.
- [x] T076 [US3] [REQ-GOAL-005] ExpenseEventConsumer — secondary flow (`ExpenseLinkedToSavingsGoalEvent`) — `services/savings-goal-service/src/main/java/com/dailyexpense/savingsgoal/consumer/ExpenseEventConsumer.java`
  - RED: `ContributionReconcileIT` (real Kafka) · AC: link event→entry `source=LINKED_EXPENSE` + total recompute; idempotent on eventId (dup→single insert). Depends: T074,T097.
- [x] T077 [US3] [REQ-GOAL-007] ContributionReconciliationService (adjusted/deleted/unlinked) — `services/savings-goal-service/src/main/java/com/dailyexpense/savingsgoal/service/ContributionReconciliationService.java`
  - RED: `ContributionReconcileIT` · AC: amount-adjusted→update entry+total; unlinked/deleted→remove entry+recompute; idempotent via processed_events. Depends: T076.
- [x] T078 [US3] [SG-INV-6] Auto-complete on total≥target + `SavingsGoalCompletedEvent` (parked) — `services/savings-goal-service/src/main/java/com/dailyexpense/savingsgoal/service/GoalLifecycleService.java`
  - RED: `SavingsGoalIT` auto-complete · AC: total≥target while ACTIVE→COMPLETED + event in same tx; fires exactly once (already COMPLETED→no second event); **SG-INV-6 double-record guard**: two concurrent reconcile calls both crossing target→exactly one COMPLETED transition (rely on `@Transactional` + DB read-before-write with status check to prevent double completion); no second `SavingsGoalCompletedEvent` in outbox. Depends: T077,T014.
- [x] T079 [US3] [REQ-GOAL-012] `PATCH /{id}/status` state machine (illegal → 409) — `services/savings-goal-service/src/main/java/com/dailyexpense/savingsgoal/service/GoalLifecycleService.java`
  - RED: `SavingsGoalIT` lifecycle · AC: valid transitions ok; COMPLETED→ACTIVE manual→409; PAUSED excluded from `?status=ACTIVE`; history preserved. Depends: T072.
- [x] T080 [US3] [REQ-GOAL-009] GoalProjectionService (avg rate; exclude PAUSED/COMPLETED) — `services/savings-goal-service/src/main/java/com/dailyexpense/savingsgoal/service/GoalProjectionService.java`
  - RED: `GoalProjectionService` unit · AC: detail has remainingAmount/percentAchieved/projectedCompletionDate; PAUSED & COMPLETED→`null`; known history→correct projection. Depends: T079,T075.
- [x] T081 [US3] [REQ-GOAL-003] `SavingsGoalDeletedEvent` → expense-service detaches (emission side) — `services/savings-goal-service/src/main/java/com/dailyexpense/savingsgoal/service/GoalLifecycleService.java`
  - RED: IT · AC: goal DELETE→`SavingsGoalDeletedEvent` written to outbox in same tx; rollback→event absent; Expense deletion is NOT cascaded (Expenses survive). Consumer implementation that sets `savings_goal_id=NULL` is in T119 (Phase 4). Depends: T072,T014.
- [x] T082 [P6] savings-goal-service integration suite (Testcontainers) — `services/savings-goal-service/src/test/java/com/dailyexpense/savingsgoal/SavingsGoalIT.java`
  - RED: `SavingsGoalIT` + `ContributionReconcileIT` first · AC: both contribution flows, reconcile on edit/delete/unlink, auto-complete once, illegal→409, foreign→403, Expenses retained on delete; Doc 04 §4/§11 tags pass. Depends: T081,T078.

### Phase 3b — User Story 4: Budget (`budget-service`)

- [x] T083 [US4] [REQ-BUD-001] Flyway V1 — `budgets` (scope CHECK, partial active index) — `services/budget-service/src/main/resources/db/migration/V1__create_budgets.sql`
  - RED: migration test · AC: `scope CHECK(OVERALL,CATEGORY)`, `ck_budgets_scope_category`, `budget_limit CHECK>0`, `period_type CHECK(WEEKLY,MONTHLY)`, `idx_budgets_active WHERE active=true`. Depends: T001,T002.
- [x] T084 [US4] [BUD-INV-5] Flyway V2 — `budget_period_ledgers` (fired_* flags, unique window) — `services/budget-service/src/main/resources/db/migration/V2__create_budget_period_ledgers.sql`
  - RED: migration test · AC: `fired_eighty_percent`/`fired_exceeded BOOLEAN DEFAULT false`, `uq_budget_period_ledgers_budget_window(budget_id,period_start)`. Depends: T083.
- [x] T085 [US4] [REQ-BUD-001] Budget + BudgetPeriodLedger entities + Scope/PeriodType enums — `services/budget-service/src/main/java/com/dailyexpense/budget/domain/Budget.java`
  - RED: entity test · AC: ledger has firedEightyPercent/firedExceeded; enums OVERALL/CATEGORY + WEEKLY/MONTHLY; not JSON-serializable (AL-4). Depends: T083,T084.
- [x] T086 [US4] [REQ-BUD-001] BudgetAuthoringService CRUD + CategoryLookupPort for CATEGORY — `services/budget-service/src/main/java/com/dailyexpense/budget/service/BudgetAuthoringService.java`
  - RED: `BudgetIT` CRUD · AC: CATEGORY+categoryId→201; OVERALL no categoryId→201; limit=0→400; category via port (AL-2); foreign→403; list `PageResponse`. Depends: T085,T046.
- [x] T087 [US4] [REQ-BUD-002] `PATCH /{id}/activation` (deactivated never alerts — BUD-INV-7) — `services/budget-service/src/main/java/com/dailyexpense/budget/controller/BudgetController.java`
  - RED: `BudgetIT` activation · AC: `{active:false}`→200; no alert on later events while inactive; reactivation resumes. Depends: T086.
- [x] T088 [US4] [REQ-BUD-003] `PATCH /{id}/rollover` toggle — `services/budget-service/src/main/java/com/dailyexpense/budget/controller/BudgetController.java`
  - RED: `BudgetPeriodIT` · AC: `{rolloverEnabled:true}`→200; at period close `carried_in`=prior unspent; disabled→`carried_in=0`. Depends: T086.
- [x] T089 [US4] [REQ-BUD-005] ExpenseEventConsumer (SpendingFeedPort) — idempotent `spent` recompute — `services/budget-service/src/main/java/com/dailyexpense/budget/consumer/ExpenseEventConsumer.java`
  - RED: `BudgetIT`/`EventFlowIT` · AC: matches by userId+categoryId (CATEGORY) or userId (OVERALL); updates `spent` atomically; idempotent via processed_events; never reads Expense schema (BUD-INV-4). Depends: T085,T097,T059.
- [x] T090 [US4] [BUD-INV-5] BudgetEvaluationService — fire 80%/exceeded once per period per threshold — `services/budget-service/src/main/java/com/dailyexpense/budget/service/BudgetEvaluationService.java`
  - RED: `BudgetIT` threshold (highest-risk) — **include `BudgetAlertKafkaRedeliveryTest`**: deliver the same `ExpenseCreatedEvent` twice via Testcontainers Kafka→`fired_eighty_percent` set exactly once, `BudgetAlertFiredEvent(EIGHTY_PERCENT)` in outbox exactly once; second delivery skipped by `processed_events` guard (T097) + flag guard. · AC: ≥80% & flag false→set flag + `BudgetAlertFiredEvent(EIGHTY_PERCENT)` same tx; ≥100%→EXCEEDED once; repeats→no extra event; deactivated→never; Kafka dup delivery→single effect (idempotency proven by named test). Depends: T089,T014.
- [x] T091 [US4] [BUD-INV-8] BudgetRolloverService + scheduler (idempotent period close/open) — `services/budget-service/src/main/java/com/dailyexpense/budget/service/BudgetRolloverService.java`
  - RED: `BudgetPeriodIT` · AC: close ledger, open new with `carried_in`=unspent if enabled else 0, `fired_*` reset; `uq` prevents dup ledger; idempotent on re-run. Depends: T088,T084.
- [x] T092 [US4] [REQ-BUD-007] BudgetStatusService `GET /{id}` derived fields — `services/budget-service/src/main/java/com/dailyexpense/budget/service/BudgetStatusService.java`
  - RED: `BudgetIT` status · AC: returns set/spent/remaining/percentUsed/firedThresholds/carriedIn; Money as `{amount,currency:"INR"}`; foreign→403. Depends: T089,T090.
- [x] T093 [P6] budget-service integration suite (Testcontainers) — `services/budget-service/src/test/java/com/dailyexpense/budget/BudgetIT.java`
  - RED: `BudgetIT` + `BudgetPeriodIT` first · AC: repeated events→one 80% + one exceeded; deactivated→none; rollover only when enabled; counters reset new period; foreign→403; Doc 04 §5/§13 tags pass; Phase 3 gate green. Depends: T092,T091.

### Phase 4 — Cross-cutting: Event-Driven Infrastructure (Transactional Outbox)  *(no US — CQ-8)*

- [x] T094 [CQ-8] Per-service `outbox` table migrations (all 5) — `services/*/src/main/resources/db/migration/Vn__create_outbox.sql`
  - RED: migration tests · AC: each service has own `outbox` (Doc 09 §7.1), `uq_outbox_event_id`, `idx_outbox_published_created`; no shared table (AL-1). Depends: T014.
- [x] T095 [CQ-8] OutboxWriter — write event in same @Transactional as state change — `services/*/.../outbox/OutboxWriter.java`
  - RED: `OutboxAtomicityIT` · AC: `write(EventEnvelope)` inserts within caller tx; rollback→row absent; `event_id`=envelope eventId. Depends: T094.
- [x] T096 [CQ-8] OutboxRelayScheduler — poll → publish to Kafka → mark published — `services/*/.../outbox/OutboxRelayScheduler.java`
  - RED: relay IT (real Kafka) · AC: polls `published=false` via index, publishes envelope, sets published+published_at; published rows skipped; message appears on topic. Depends: T095,T002.
- [x] T097 [CQ-8] `processed_events` table + idempotent-consume guard (all consuming services) — `services/{savings-goal,budget,expense}-service/.../consumer/ProcessedEventGuard.java`
  - RED: dedup IT · AC: `processed_events(event_id PK,...)`; insert-before-process; duplicate eventId→skip; duplicate Kafka delivery→single effect; expense-service also gets `processed_events` migration (needed by T119 SavingsGoalDeletedEventConsumer). Depends: T094.
- [x] T098 [CQ-8] Standard EventEnvelope in shared-kernel — `shared-kernel/src/main/java/com/dailyexpense/shared/outbox/EventEnvelope.java`
  - RED: `EventEnvelopeTest` · AC: 8 fields (eventId,eventType,eventVersion,occurredAt,producer,userId,traceId,payload); round-trip identical; none missing in JSON. Depends: T001.
- [x] T099 [P6] Event-flow integration test (expense → budget + goal, real Kafka) — `services/expense-service/src/test/java/com/dailyexpense/EventFlowIT.java`
  - RED: `EventFlowIT` first · AC: Expense create→budget `spent` updated AND goal `total_contributed` updated; dup delivery→single effect; no cross-schema SQL (AL-1); Phase 4 gate green. Depends: T096,T097.
- [x] T119 [US3] [REQ-GOAL-003] [AL-1] SavingsGoalDeletedEventConsumer — expense-service clears `savings_goal_id` — `services/expense-service/src/main/java/com/dailyexpense/expense/consumer/SavingsGoalDeletedEventConsumer.java`
  - RED: `SavingsGoalDeletedConsumeIT` (real Kafka + Testcontainers) · AC: `SavingsGoalDeletedEvent` consumed by expense-service→`UPDATE expenses SET savings_goal_id=NULL WHERE savings_goal_id=:deletedGoalId AND user_id=:userId`; Expenses NOT deleted; idempotent via `processed_events` in `expense_db` (dup `eventId`→skip, no double UPDATE); **no `savings_goal_db` SQL** (AL-1). Phase 4 gate: `SavingsGoalDeletedConsumeIT` green closes the CUJ-10 cross-service contract. Depends: T056,T097,T081.

### Phase 5 — Frontend (React 18 + TS strict)  *(US labels per feature)*

- [x] T100 [FE-3] [FE-7] Vite + React 18 + TS strict scaffold + Tailwind CSS + shadcn/ui init — `frontend/tsconfig.json`, `tailwind.config.ts`, `components.json`, `src/globals.css`
  - RED: `tsc --noEmit` gate + registry-compliance lint (grep for unregistered UI packages) · AC: 0 tsc errors; `vite build` ok; Tailwind PostCSS configured; `npx shadcn@latest init` config committed (`components.json`); all packages in `package.json` appear in Doc 15 §3 registry; no `any`. Depends: T001.
- [x] T101 [FE-6] apiConfig — env-based base URLs (no hardcoded URLs) — `frontend/src/lib/apiConfig.ts`
  - RED: lint/grep gate · AC: base URL from `import.meta.env.VITE_API_BASE_URL`; no hardcoded `http://` in any `.ts`/`.tsx`. Depends: T100.
- [x] T102 [FE-1] [FE-2] Single axiosClient + single-flight refresh interceptor — `frontend/src/lib/axiosClient.ts`
  - RED: `axiosClient` Vitest (MSW) · AC: exactly one Axios instance; two concurrent 401s→exactly one `POST /auth/refresh`; queued requests replayed; refresh-fail→clear+redirect. Depends: T101.
- [x] T103 [FE-2] Auth store (in-memory access token) + ProtectedRoute — `frontend/src/features/auth/authStore.ts`
  - RED: auth-store Vitest · AC: token in memory (not localStorage/sessionStorage); `clearTokens()`→`getAccessToken()` null; ProtectedRoute redirects when unauthenticated. Depends: T102.
- [x] T104 [REQ-USR-003] Auth pages: login/register/verify/forgot/reset — `frontend/src/features/auth/`
  - RED: RTL tests (loading/error/success) · AC: each page renders loading/error/success; client-side required-field validation; `tsc` strict clean. Depends: T103.
- [x] T105 [FE-4] [FE-7] Shared LoadingState(shadcn Skeleton)/ErrorState(shadcn Alert)/EmptyState(shadcn Card+lucide) + PaginatedTable(@tanstack/react-table + shadcn Table) + MoneyDisplay(Intl.NumberFormat en-IN) + DateDisplay(date-fns enIN) — `frontend/src/components/`
  - RED: RTL for all 6 · AC: states render correct fallback; `LoadingState` has `aria-busy="true"`; PaginatedTable consumes `PageResponse<T>`; MoneyDisplay→`₹{amount}` via `Intl.NumberFormat('en-IN',{style:'currency',currency:'INR'})`; DateDisplay uses `date-fns` `enIN` locale; all lib imports from Doc 15 §3 registry; no `any`. Depends: T100.
- [x] T106 [US1] [REQ-CAT-001] Categories feature (list/form, default vs custom) — `frontend/src/features/categories/`
  - RED: RTL · AC: DEFAULT shows no/disabled delete (`deletable:false`); custom create/edit; `?type=` filter; `tsc` clean. Depends: T105,T043.
- [x] T107 [US1] [REQ-EXP-001] Expenses feature (list+filters+sort+pagination, form, receipts, tags, recurring, import/export) — `frontend/src/features/expenses/`
  - RED: RTL (loading/error/empty) · AC: filters update list; form validates amount/date/category/method client-side; receipt pre-validate >5MB/type; import+export controls; `tsc` clean. Depends: T105,T054,T055.
- [x] T108 [US3] [REQ-GOAL-001] Savings Goals (list active/completed, detail+progress+projection+history, contribution form) — `frontend/src/features/savings-goals/`
  - RED: RTL · AC: ACTIVE/COMPLETED split; detail shows remaining/percent/projection; history paginated; contribution form validates amount>0+date; status control; `tsc` clean. Depends: T105,T072,T075.
- [x] T109 [US4] [REQ-BUD-001] Budgets (list+status cards, form, activation/rollover toggles) — `frontend/src/features/budgets/`
  - RED: RTL · AC: status card set/spent/remaining/percentUsed; form validates limit>0; activation/rollover toggles call PATCH; loading/error/empty; `tsc` clean. Depends: T105,T086,T092.
- [x] T110 [REQ-A11Y] Accessibility (ARIA/keyboard/contrast) + responsive breakpoints — `frontend/src/`
  - RED: axe-core + viewport tests · AC: ARIA labels/roles; keyboard nav (Tab/Enter/Esc); WCAG AA contrast (0 serious/critical); renders at 320/768/1024px. Depends: T107,T108,T109.
- [x] T111 [P6] Frontend test suite (Vitest + RTL + MSW) — `frontend/src/**/__tests__/`
  - RED: suite first · AC: `vitest run` 100%; refresh interceptor proven; loading/error/empty per data view; no `any`; `tsc --noEmit` clean; Phase 5 gate green. Depends: T110,T102.

### Phase 6 — Cross-Service Data Export Aggregation & Business Observability  *(REQ-USR-011, REQ-OBS-006/CQ-14)*

- [ ] T112 [REQ-USR-011] UserDataPort interface + DataExportAggregator (fan-out to 4 service adapters → ZIP → MinIO → READY) — `services/user-service/src/main/java/com/dailyexpense/user/port/UserDataPort.java`
  - RED: `UserDataExportIT` aggregation path · AC: `UserDataPort.exportUserData(userId)` returns `UserExportSegment`; aggregator fans out to T114–T117 adapters + local user profile; ZIP uploaded to MinIO; export status→READY; `DataExportReadyEvent` written to outbox in same tx (CQ-8). Depends: T035,T014.
- [ ] T113 [REQ-USR-011] UserDataPort contract test (all 4 adapters: AL-1 verified, 0 cross-service SQL) — `services/user-service/src/test/java/com/dailyexpense/user/port/UserDataPortContractTest.java`
  - RED: contract test per adapter · AC: each adapter returns non-null `UserExportSegment` for known userId; empty segment (not error) for userId with no data; ArchUnit confirms 0 cross-service DB reads per adapter. Depends: T112,T114,T115,T116,T117.
- [ ] T114 [P] [REQ-USR-011] category-service internal endpoint `GET /internal/users/{userId}/export-data` + `CategoryUserDataAdapter` in user-service — `services/category-service/src/main/java/com/dailyexpense/category/port/CategoryUserDataController.java`
  - RED: `CategoryIT` export-data path · AC: returns all categories (DEFAULT + owned custom) for userId; endpoint guarded by service token; no `expense_db`/`savings_goal_db`/`budget_db` SQL (AL-1). Depends: T048,T112.
- [ ] T115 [P] [REQ-USR-011] expense-service internal endpoint `GET /internal/users/{userId}/export-data` + `ExpenseUserDataAdapter` in user-service — `services/expense-service/src/main/java/com/dailyexpense/expense/port/ExpenseUserDataController.java`
  - RED: `ExpenseIT` export-data path · AC: returns all expenses+tags+receipt-refs for userId; streamed (CQ-10, no full in-memory); no other service DB SQL (AL-1). Depends: T068,T112.
- [ ] T116 [P] [REQ-USR-011] savings-goal-service internal endpoint `GET /internal/users/{userId}/export-data` + `SavingsGoalUserDataAdapter` in user-service — `services/savings-goal-service/src/main/java/com/dailyexpense/savingsgoal/port/SavingsGoalUserDataController.java`
  - RED: `SavingsGoalIT` export-data path · AC: returns all goals+contribution history for userId; no other service DB SQL (AL-1). Depends: T082,T112.
- [ ] T117 [P] [REQ-USR-011] budget-service internal endpoint `GET /internal/users/{userId}/export-data` + `BudgetUserDataAdapter` in user-service — `services/budget-service/src/main/java/com/dailyexpense/budget/port/BudgetUserDataController.java`
  - RED: `BudgetIT` export-data path · AC: returns all budgets+ledger history for userId; no other service DB SQL (AL-1). Depends: T093,T112.
- [ ] T118 [P] [REQ-OBS-006/CQ-14] Micrometer business metrics: counters `expenses.created`, `users.registered`, `budget.alerts.sent`, `goals.completed` — `services/*/src/main/java/com/dailyexpense/*/observability/BusinessMetrics.java`
  - RED: counter assertion (`GET /actuator/metrics/{name}` → `{name,measurements}`) per service · AC: counter increments after each corresponding event; `GET /actuator/metrics` index includes business counter names; 0 PII in metric tags. Depends: T057,T025,T090,T078.

---

## 3. Definition of Done (DoD) & Review Gates

> A phase advances only when its gate is **fully green**. Each task also satisfies the 3-Commit Loop:
> RED (test fails for the right reason) → GREEN (passes) → REFACTOR (MDC + DTO mapping, no behaviour change).

### Universal per-task gate (every T###)

- [ ] 3 commits in order: `test(RED) → feat(GREEN) → refactor(REFACTOR)`
- [ ] No `null` from any service method (`Optional<T>` only) — CQ-2
- [ ] No JPA entity on any controller signature (DTO-only) — AL-4
- [ ] Business logic only in `service/`; controllers thin; repos data-only — CQ-1
- [ ] Every write `@Transactional`; outbox write in the same tx — CQ-8
- [ ] Multi-query reads annotated `@Transactional(readOnly=true)` — CQ-4
- [ ] `traceId` on every log line; **0 PII** (email/name/amount/token) — CQ-12/13
- [ ] **0 magic literals** (enums/constants) — CQ-3/DB-7
- [ ] No hardcoded secrets (env only) — SEC-6 · versioned `/api/v1` — API-1 · envelopes — API-2/3

### Phase 0 gate — Foundation
- [ ] `docker compose up` healthy; CI green end-to-end
- [ ] shared-kernel unit tests green (JwtService/PiiMasker/MoneyDto); ≥80% on `security/`
- [ ] GlobalExceptionHandler returns uniform envelope for 400/401/403/404/409/429 (contract test)

### Phase 1 gate — Identity & Access
- [ ] All Doc 04 §2 BDD pass as Testcontainers ITs; BCrypt cost ≥12 asserted in DB
- [ ] Refresh rotation: old→401; reused revoked → **entire family revoked**
- [ ] Every `/{id}`/`/me`: foreign→**403 never 404**; lists isolate by user; 429+`Retry-After`; contract-diff clean

### Phase 2 gate — Core Domains
- [ ] category & expense Testcontainers suites green (incl. CategoryLookupPort cross-service validation)
- [ ] Defaults seeded; DEFAULT edit/delete→403/409; in-use delete→409
- [ ] Receipt pdf/oversize/magic-mismatch→400; **EXIF 0 segments** on stored bytes; CSV injection neutralized + Idempotency-Key dedup; money `NUMERIC(19,4)` end-to-end (0 float)

### Phase 3 gate — Advanced Domains
- [ ] savings-goal & budget suites green; one ContributionEntry per backing Expense (unique proven)
- [ ] Total reconciles on edit/delete/unlink; auto-complete fires **exactly once**; illegal goal status→409
- [ ] Budget threshold fires **once per period per threshold**; deactivated fires none; rollover only when enabled

### Phase 4 gate — Event Infrastructure
- [ ] Outbox write + state change atomic (rollback leaks no event); consumers idempotent on `eventId`
- [ ] Cross-service event-flow IT green (expense → budget + goal); ArchUnit AL-1 (no cross-schema SQL) green
- [ ] `SavingsGoalDeletedConsumeIT` green: expense-service sets `savings_goal_id=NULL` on goal delete; idempotent on dup Kafka delivery (T119)

### Phase 5 gate — Frontend
- [ ] `tsc --noEmit` strict clean (0 `any`); exactly one Axios instance; no raw `fetch`/hardcoded URLs
- [ ] Transparent refresh proven via MSW; Loading/Error/Empty per data view; a11y + responsive pass
- [ ] FE-7 registry compliance: every UI/styling/charting/form import in `frontend/src/` appears in `15-ui-design-system.md` §3 approved registry; no unregistered package; no `recharts` import in any Phase-1 component

### Phase 6 gate — Data Export Aggregation & Observability
- [ ] `UserDataPortContractTest` green; all 4 adapters confirm 0 cross-service DB SQL (AL-1)
- [ ] Full export ZIP assembled (user profile + categories + expenses+tags + goals+contributions + budgets+ledgers); download→200 owner / 403 foreign
- [ ] Business metrics at `/actuator/metrics/{name}` increment correctly for all 5 services; 0 PII in metric tags

### Final release gate — G-01..G-16 (from 14-test-strategy.md §13.1) — Phase-1 "production-ready" definition
- [ ] **G-01** `mvn test` on 5 services + shared-kernel: 100% pass
- [ ] **G-02** `vitest run` 100%; `tsc --noEmit` 0 errors; 0 `any`
- [ ] **G-03** All Testcontainers ITs green (AuthFlow, PasswordReset, UserProfile, Category, ExpenseCrud, ExpenseFilter, Receipt, Tag, RecurringExpense, CsvImport, CsvExport, SavingsGoal, ContributionReconcile, Budget, BudgetPeriod, EventFlow, OutboxAtomicity)
- [ ] **G-04** ArchUnit fitness: 0 violations (no entity in controller, no cross-service import, Optional-only, BigDecimal money, no secret strings, @Transactional on writes)
- [ ] **G-05** OpenAPI contract-diff: 0 breaking changes vs baseline (all 51 endpoint groups represented)
- [ ] **G-06** All 12 CUJs green in Docker Compose E2E
- [ ] **G-07** 403-never-404 covered for **every** aggregate root (Expense, Receipt, Tag, RecurringExpense, SavingsGoal, Budget, DataExport, ContributionEntry)
- [ ] **G-08** EXIF strip verified: metadata-extractor → 0 EXIF segments on stored receipt bytes (SEC-5 release blocker)
- [ ] **G-09** PII log scan: 0 raw email/amount/password in any service log
- [ ] **G-10** Cross-schema SQL: 0 queries from any service to another's database
- [ ] **G-11** No `null` return in any service method (ArchUnit + spot grep)
- [ ] **G-12** No hardcoded secret in source (ArchUnit regex + git-secrets)
- [ ] **G-13** `GET /actuator/health` → 200 `{"status":"UP"}` for all 5 services
- [ ] **G-14** axe-core: 0 serious/critical violations on all pages
- [ ] **G-15** Performance p95 ≤ §7.1 targets (advisory; blocks only if > 2× target)
- [ ] **G-16** REQ-* traceability: every Phase-1 REQ-* has ≥1 passing test
- [ ] **Scope confirmed:** Income / Reporting / Notification consumer **NOT** implemented (spec.md §7)

### Coverage floors (14-test-strategy.md §13.2)
`service/` 85% · `security/` 90% · `domain/` 80% · `controller/` 70% · `repository/` 60% · frontend `src/` 70%.

---

## 4. Dependencies & Execution Order

- **Phase 0 (Setup/Foundational)** blocks everything. **Phase 1 (user-service)** and **Phase 2a
  (category-service)** are foundational prerequisites (auth + category lookup) for the story phases.
- **Story phases:** US1/US2/US5/US6/US7 (expense-service, Phase 2b) → US3 (savings-goal, Phase 3a) →
  US4 (budget, Phase 3b). Goal/budget event flows additionally depend on **Phase 4** outbox infra (T094–T099).
- **Phase 5 (frontend)** features depend on their backend endpoints (per-task Depends-On) + shared components T105.
- Cross-service Depends-On to note: T076/T089/T119 depend on T097 (processed_events guard); T073 depends on T046
  (CategoryLookupPort); T081 emits `SavingsGoalDeletedEvent`; T119 (Phase 4) is the expense-service consumer that detaches the goal reference; T112 interface is wired by T114–T117
  adapters — T113 (contract test) depends on all 4 to be complete.

### Parallel opportunities `[P]`
- Phase 0: T004, T005, T007, T008, T011, T013, T015 (disjoint shared-kernel files).
- Phase 1: T018/T019/T020 (independent migrations), T024.
- Phase 2: migrations T050/T051/T052 across disjoint files.
- Across services: Flyway V1 migrations (T040, T049, T069, T083) are mutually independent once T001/T002 done.
- Phase 6: T114, T115, T116, T117 (UserDataPort adapters across 4 different services — all [P] after T112); T118 (observability — [P] across services).
- Do **not** parallelize tasks sharing a file (e.g. T043/T044/T045 all touch CategoryAuthoringService).

---

## 5. Implementation Strategy

### MVP first (suggested) — User Story slice = identity-enabled Expense capture
1. **Phase 0** (Setup/Foundational shared-kernel) → infra + envelopes + JWT + outbox contract.
2. **Phase 1** (user-service) → register/verify/login/refresh/logout + profile (auth that every story needs).
3. **Phase 2a** (category-service) → defaults + CategoryLookupPort.
4. **US1** (expense-service core: T049–T057, T067) → **STOP & VALIDATE**: a user can register, log in,
   and record/list/edit/delete Expenses end-to-end. This is the demoable MVP.

### Incremental delivery
US5 (receipts) → US6/US7 (CSV) → US2 (recurring) → US3 (savings goals) → US4 (budgets) → Phase 4 hardening
→ Phase 5 frontend per feature. Each story is independently testable via its `*IT` suite before the next.

---

## 6. Generation Summary

| Metric | Value |
|--------|-------|
| Total tasks | **119** (T001–T119) |
| Phase 0 (Setup/Foundational) | T001–T015 (15) |
| Phase 1 (user-service, foundational) | T016–T039 (24) |
| Phase 2a (category-service, foundational) | T040–T048 (9) |
| Phase 2b (expense-service — US1/US2/US5/US6/US7) | T049–T068 (20) |
| Phase 3a (savings-goal-service — US3) | T069–T082 (14) |
| Phase 3b (budget-service — US4) | T083–T093 (11) |
| Phase 4 (outbox infra, cross-cutting) | T094–T099 + T119 (7) |
| Phase 5 (frontend) | T100–T111 (12) |
| Phase 6 (data export aggregation + observability) | T112–T118 (7) |

**Tasks per user story** (primary owner; foundational tasks enable all):
US1 ≈ 12 (T049–T057,T062,T067,T106,T107) · US2 ≈ 4 (T052,T063,T064 + UI in T107) ·
US3 ≈ 16 (T069–T082,T108) · US4 ≈ 13 (T083–T093,T109) · US5 ≈ 3 (T050,T060,T061) ·
US6 ≈ 1 (T065) · US7 ≈ 1 (T066). REQ-USR-011 (full export): T112–T117. Foundational (no US): T001–T048, T094–T105, T110–T111, T118.

**Suggested MVP scope:** User Story 1 (record Expense) on top of Phase 0 + Phase 1 + Phase 2a.

**Format validation:** every task has checkbox + sequential ID + `[P]` where parallel + US/law label +
exact file path + a RED test artifact + a binary acceptance criterion.

*End of `12-implementation-plan.md` — the wired SpecKit TASKS artifact (self-sufficient for `/speckit-implement`).*

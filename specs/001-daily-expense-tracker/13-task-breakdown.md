# Task Breakdown — Daily Expense Application

| Field | Value |
|-------|-------|
| **Document** | 13 — Atomic Task Breakdown |
| **Feature** | Daily Expense Application |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Status** | Ready for execution |
| **Created** | 2026-06-27 |
| **Governing Authority** | [Engineering Constitution](../../.specify/memory/constitution.md) + [Doc 11 Agent Pack](./11-agent-instruction-pack.md) |
| **Phase Authority** | [12-implementation-plan.md](./12-implementation-plan.md) — phases and T### IDs are canonical; this document breaks each into TASK-NNN with full traceability |
| **Vocabulary Authority** | [02-glossary.md](./02-glossary.md) — all entity/field/table names use exact Glossary terms |
| **Requirement Authority** | [03-requirement-catalogue.md](./03-requirement-catalogue.md) — every TASK traces to ≥1 REQ-* |

> **Rules applied to every TASK below:**
> - Traces to ≥ 1 REQ-* ID (or a Constitution law where no REQ-* covers the infrastructure).
> - Entity/field/table names are exact Glossary terms: `Expense`, `Contribution`, `SavingsGoal`, `Budget`, `PaymentMethod`, `GoalStatus`, `BudgetPeriodType`, `CategoryType`, etc.
> - No cross-schema DB access (AL-1); cross-context refs are bare `UUID` columns validated via Anti-Corruption Ports (AL-2).
> - Service methods return `Optional<T>`, never `null` (CQ-2).
> - Every domain event written in the same `@Transactional` as the state change (outbox pattern, CQ-8).
> - Tasks align with the phase structure of `12-implementation-plan.md`; phases are NOT redefined here.
> - Each TASK is independently executable in a single focused session.
> - Acceptance Criteria are verifiable: a passing test name or a visible artifact.

---

## Phase Index

| Phase | Tasks | Description |
|-------|-------|-------------|
| Phase 0 — Foundation & Shared Kernel | TASK-001 … TASK-015 | Cross-cutting infrastructure every service depends on |
| Phase 1 — Identity & Access | TASK-016 … TASK-039 | `user-service`: User aggregate, JWT, Token Family rotation, auth lifecycle |
| Phase 2 — Core Domains | TASK-040 … TASK-068 | `category-service` + `expense-service`: Categories, Expenses, Receipts, Tags, Recurring, CSV |
| Phase 3 — Advanced Domains | TASK-069 … TASK-093 | `savings-goal-service` + `budget-service`: Contributions, Goal lifecycle, Budget thresholds |
| Phase 4 — Event-Driven Infrastructure | TASK-094 … TASK-099 | Transactional Outbox relay, processed_events idempotency guard, EventEnvelope |
| Phase 5 — Frontend Integration | TASK-100 … TASK-111 | React 18 + TypeScript strict, axiosClient, feature UIs |

---

## Phase 0 — Foundation & Shared Kernel

### TASK-001 — Initialize parent Maven aggregator POM

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | — |
| Spec Refs | Constitution CQ-5, CQ-6, P6; Doc 12 §1.1 monorepo structure |
| Acceptance Criteria | `mvn verify` on the parent POM completes without error; `shared-kernel` module resolves in all five service POMs; Java 21 + Spring Boot 3.x BOM declared; JUnit 5, Mockito, Testcontainers dependencies present |

---

### TASK-002 — Author Docker Compose (infra services)

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-001 |
| Spec Refs | Constitution CQ-6 (Testcontainers needs local broker + DB), AL-1 (five isolated databases); Doc 12 §1.1 |
| Acceptance Criteria | `docker-compose up` completes healthy: postgres×5 (one per service — `identity_db`, `category_db`, `expense_db`, `savings_goal_db`, `budget_db`), MinIO, Kafka, ZooKeeper, MailHog; each PostgreSQL instance is on a distinct port with no shared schema |

---

### TASK-003 — CI pipeline definition

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-001 |
| Spec Refs | Constitution P6 (Untested Code Does Not Exist), CQ-5, CQ-6, CQ-7; Doc 12 Phase 0 gate |
| Acceptance Criteria | `.github/workflows/ci.yml` exists with stages: build → `tsc` → lint → unit → Testcontainers integration → OpenAPI contract-diff; a push triggers the pipeline; all stages green on the scaffold commit |

---

### TASK-004 — PageResponse\<T\> uniform pagination envelope

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-001 |
| Spec Refs | REQ-API-002, Constitution API-2; Doc 07 §1.3 |
| Acceptance Criteria | `PageResponse<String>` serializes exactly five keys: `content`, `page`, `size`, `totalElements`, `totalPages`; unit test asserts no extra keys; generic type parameter is preserved in JSON output |

---

### TASK-005 — ErrorResponse + ApiError uniform error envelope

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-001 |
| Spec Refs | REQ-API-003, Constitution API-3, CQ-13 (no PII in message); Doc 07 §1.4 |
| Acceptance Criteria | `ErrorResponse` serializes exactly six keys: `timestamp`, `status`, `error`, `message`, `path`, `traceId`; unit test asserts `message` field contains no `email`, `amount`, or token value |

---

### TASK-006 — GlobalExceptionHandler @ControllerAdvice

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-005, TASK-007 |
| Spec Refs | REQ-API-003, REQ-API-004, Constitution API-3, API-4; Doc 07 §1.4 |
| Acceptance Criteria | Mockito test for each mapped exception confirms HTTP status: 400 (validation), 401 (unauthenticated), 403 (`ForbiddenOwnershipException`), 404 (`ResourceNotFoundException`), 409 (`BusinessConflictException`), 429 (rate limit); response body is `ErrorResponse` in all cases; no PII in `message` (asserted by log capture) |

---

### TASK-007 — Domain exception classes

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-001 |
| Spec Refs | REQ-SEC-003, Constitution SEC-3, P4; Doc 10 §3.1 (403-never-404 rule) |
| Acceptance Criteria | `ForbiddenOwnershipException`, `ResourceNotFoundException`, `BusinessConflictException` classes present in `shared-kernel`; unit test: throwing `ForbiddenOwnershipException` in a service causes `GlobalExceptionHandler` to return HTTP 403 (not 404) |

---

### TASK-008 — MoneyDto (BigDecimal + currency)

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-001 |
| Spec Refs | Constitution DB-5; Doc 07 §1.5; Doc 09 §1.1 rule DB-5 |
| Acceptance Criteria | `MoneyDto` with `amount: 100.50, currency: "INR"` serializes to `{"amount":"100.50","currency":"INR"}` (scale-2 string); construction with a `double` is compile-rejected or throws at runtime; construction with currency != `"INR"` throws; unit test covers all three assertions |

---

### TASK-009 — JwtService (HS256 sign/verify)

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-001 |
| Spec Refs | REQ-SEC-002, Constitution SEC-2; Doc 10 §2.2 |
| Acceptance Criteria | Unit tests: (1) signed token verifies successfully and `sub` equals the user UUID (not email); (2) token with `exp` in the past → `JwtException`; (3) token with `typ: "refresh"` presented to `access` validator → rejected; (4) `JWT_SECRET` loaded from environment variable — never hardcoded (SEC-6) |

---

### TASK-010 — JwtAuthenticationFilter

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-009 |
| Spec Refs | Constitution AL-5 (stateless identity), SEC-2, CQ-12; Doc 10 §3.2 |
| Acceptance Criteria | Valid JWT → `SecurityContext` principal equals `userId` UUID; MDC contains `userId` after filter runs; expired/invalid JWT → filter returns 401, no `SecurityContext` set; `email` and `full_name` never appear in MDC (only opaque UUID) |

---

### TASK-011 — TraceIdFilter

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-001 |
| Spec Refs | Constitution CQ-12, P7; Doc 11 §3 REFACTOR commit |
| Acceptance Criteria | For every request, MDC contains key `traceId` with a non-null UUID; each request produces a distinct `traceId`; `traceId` is cleared from MDC after response (no cross-request bleed); unit test asserts both distinctness and cleanup |

---

### TASK-012 — RequestLoggingFilter

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-011 |
| Spec Refs | Constitution CQ-11, P7; Doc 10 §4.3 |
| Acceptance Criteria | Log line for each request contains `method`, `path`, `status`, `latencyMs`, `traceId`; log line does NOT contain `email`, `amount`, password, or `Authorization` header value; unit test captures log output and asserts both presence and absence conditions |

---

### TASK-013 — PiiMasker

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-001 |
| Spec Refs | REQ-OBS-004, Constitution CQ-13; Doc 10 §4.3 |
| Acceptance Criteria | Unit tests: (1) `maskEmail("asha@example.in")` → first char of local + domain masked (e.g. `a****@e****.in`); (2) `maskName("Asha Rao")` → `A****`; (3) token/hash value → `***REDACTED***`; (4) amounts are never passed to `PiiMasker` — a test confirms the utility has no `maskAmount` method (amounts are omitted, not masked) |

---

### TASK-014 — OutboxEntry + OutboxPublisher interface

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-001 |
| Spec Refs | Constitution CQ-8 (outbox in same transaction); Doc 08 §1.3; Doc 09 §7.1 |
| Acceptance Criteria | `OutboxEntry` maps to `outbox` table columns: `id`, `event_id`, `aggregate_type`, `aggregate_id`, `event_type`, `payload JSONB`, `published BOOLEAN DEFAULT false`, `created_at`, `published_at NULL`; `OutboxPublisher` interface declares `publish(EventEnvelope)` returning void; unit test confirms the interface contract compiles and the entity maps correctly |

---

### TASK-015 — Shared-kernel unit test suite (RED→GREEN)

| Field | Value |
|-------|-------|
| Phase | Phase 0 — Foundation & Shared Kernel |
| Depends On | TASK-009, TASK-013, TASK-008 |
| Spec Refs | Constitution P6, CQ-5; Doc 11 §3 (3-Commit Loop) |
| Acceptance Criteria | `mvn test -pl shared-kernel` passes 100%; `JwtService`, `PiiMasker`, `MoneyDto` each have explicit RED (failing) → GREEN (passing) test commits; coverage on `security/` package ≥ 80%; Phase 0 gate from Doc 12 §3 confirmed green |

---

## Phase 1 — Identity & Access (`user-service`)

### TASK-016 — Flyway V1: `users` table + `set_updated_at()` trigger

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-001, TASK-002 |
| Spec Refs | REQ-USR-001, Constitution DB-3, DB-7, DB-8; Doc 09 §2.1 |
| Acceptance Criteria | `V1__create_users.sql` applies cleanly on a fresh `identity_db`; table has `id UUID PRIMARY KEY`, `status VARCHAR(24) CHECK(status IN ('INACTIVE_UNVERIFIED','ACTIVE','DELETED'))`, `password_hash VARCHAR(72) NOT NULL`, `created_at` and `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`; `set_updated_at()` trigger fires on `UPDATE` and advances `updated_at`; `uq_users_email` unique constraint exists |

---

### TASK-017 — Flyway V2: `refresh_tokens` table

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-016 |
| Spec Refs | REQ-SEC-002, Constitution SEC-2; Doc 09 §2.3; Doc 10 §2.8 |
| Acceptance Criteria | `V2__create_refresh_tokens.sql` creates `refresh_tokens` with `family_id UUID NOT NULL`, `token_hash VARCHAR(255) UNIQUE`, `expires_at TIMESTAMPTZ NOT NULL`, `revoked_at TIMESTAMPTZ NULL`; indexes `idx_refresh_tokens_family_id`, `idx_refresh_tokens_expires_at`, `idx_refresh_tokens_user_id` all exist |

---

### TASK-018 — Flyway V3: `email_verifications` table

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-016 |
| Spec Refs | REQ-USR-004; Doc 09 §2.2 |
| Acceptance Criteria | `V3__create_email_verifications.sql` creates table with `token_hash VARCHAR(255) UNIQUE`, `consumed_at TIMESTAMPTZ NULL`, `expires_at TIMESTAMPTZ NOT NULL`; FK `fk_email_verifications_user_id → users(id) ON DELETE CASCADE`; migration applies cleanly with no errors |

---

### TASK-019 — Flyway V4: `password_reset_tokens` table

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-016 |
| Spec Refs | REQ-USR-007; Doc 09 §2.4 |
| Acceptance Criteria | `V4__create_password_reset_tokens.sql` creates table with `token_hash VARCHAR(255) UNIQUE`, `consumed_at TIMESTAMPTZ NULL`, `expires_at TIMESTAMPTZ NOT NULL`; FK → `users(id) ON DELETE CASCADE`; Flyway validates on startup with no checksum errors |

---

### TASK-020 — Flyway V5: `data_exports` table

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-016 |
| Spec Refs | REQ-USR-011; Doc 09 §2.5 |
| Acceptance Criteria | `V5__create_data_exports.sql` creates table with `status VARCHAR(16) CHECK(status IN ('REQUESTED','READY','FAILED'))`, `download_ref VARCHAR(512) NULL`; FK → `users(id) ON DELETE CASCADE`; migration applies cleanly |

---

### TASK-021 — User JPA entity + UserStatus enum

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-016 |
| Spec Refs | REQ-USR-001, Constitution AL-4 (entity never serialized); Doc 09 §2.1; Doc 02 Glossary (User) |
| Acceptance Criteria | `User.java` JPA entity has all columns from `users` table; `UserStatus` enum declares `INACTIVE_UNVERIFIED`, `ACTIVE`, `DELETED`; entity carries no `@JsonProperty` or Jackson annotation that would expose it via HTTP; no `User` entity appears in any `*Response` DTO (compilation check) |

---

### TASK-022 — UserRepository (Spring Data JPA, Optional returns)

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-021 |
| Spec Refs | Constitution CQ-2 (Optional\<T\> for lookups), DB-6; Doc 09 §2.1 |
| Acceptance Criteria | `UserRepository` extends `JpaRepository<User, UUID>`; `findByEmail(String)` return type is `Optional<User>` (not `User`); unit test: `findByEmail` for absent email returns `Optional.empty()`; `findById` return type is `Optional<User>` |

---

### TASK-023 — RegistrationService (register + 409 on duplicate email)

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-022, TASK-024 |
| Spec Refs | REQ-USR-003, Constitution SEC-1, CQ-1 (logic in Service), CQ-2; Doc 04 §2 BDD (register scenarios) |
| Acceptance Criteria | 3-Commit Loop complete; RED: `register("asha@example.in")` on existing email throws `BusinessConflictException`; GREEN: service creates `User` with `status=INACTIVE_UNVERIFIED` and BCrypt-hashed password (never plaintext); REFACTOR: log capture asserts no plaintext password appears in any log line |

---

### TASK-024 — BCrypt password encoder config (cost ≥ 12)

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-001 |
| Spec Refs | REQ-SEC-001, Constitution SEC-1; Doc 10 §1.1 |
| Acceptance Criteria | `PasswordEncoderConfig` Spring `@Bean` returns `BCryptPasswordEncoder` with cost factor ≥ 12; unit test: `encoder.encode("pwd")` produces a hash beginning with `$2a$12$`; plaintext value is not recoverable from the stored hash |

---

### TASK-025 — EmailVerificationService + `UserRegisteredEvent` via outbox

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-023, TASK-014, TASK-018 |
| Spec Refs | REQ-USR-004, Constitution CQ-8 (outbox in same tx); Doc 08 (`UserRegisteredEvent`); Doc 09 §2.2 |
| Acceptance Criteria | On successful registration, `email_verifications` row AND `outbox` row (event_type `UserRegisteredEvent`) inserted in the SAME `@Transactional`; rollback test: if `email_verifications` insert fails, outbox row is also absent; `token_hash` stored is SHA-256 of the raw token (raw token never in DB); Testcontainers IT confirms atomicity |

---

### TASK-026 — AuthController: `POST /auth/register` + `GET /auth/verify-email`

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-025, TASK-006 |
| Spec Refs | REQ-USR-003, REQ-USR-004, Constitution API-1 (versioned path), API-4 (201 + Location); Doc 07 §2.1 |
| Acceptance Criteria | `POST /api/v1/auth/register` returns 201 + `Location` header on success; duplicate email → 409 with `ErrorResponse`; `GET /api/v1/auth/verify-email?token=<opaque>` activates `User.status` to `ACTIVE` and returns 200; Testcontainers IT covers both happy paths and the 409 |

---

### TASK-027 — AuthenticationService: `POST /auth/login`

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-022, TASK-024, TASK-009 |
| Spec Refs | REQ-USR-005, Constitution SEC-2; Doc 10 §2.3; Doc 07 §2.2 |
| Acceptance Criteria | Correct credentials → `AuthTokenResponse` with `accessToken` (15-min JWT, `expiresInSec:900`), `refreshToken` (opaque), `tokenType:"Bearer"`; wrong password → 401 generic message; `INACTIVE_UNVERIFIED` status → 401 same generic message (no account-state disclosure); plaintext password absent from all log lines |

---

### TASK-028 — RefreshToken entity + RefreshTokenRepository

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-017 |
| Spec Refs | REQ-SEC-002, Constitution SEC-2; Doc 09 §2.3; Doc 10 §2.4 |
| Acceptance Criteria | `RefreshToken.java` JPA entity maps all columns including `family_id UUID NOT NULL`; `RefreshTokenRepository` has `findByTokenHash(String)` returning `Optional<RefreshToken>` and `findAllByFamilyId(UUID)` for family-wide revocation; only SHA-256 hash stored — no raw token value in any column |

---

### TASK-029 — TokenRotationService: `POST /auth/refresh` (rotation + family revocation)

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-027, TASK-028 |
| Spec Refs | REQ-SEC-002, Constitution SEC-2; Doc 10 §2.4, §2.5 |
| Acceptance Criteria | 3-Commit Loop; (1) valid Refresh Token → new Access Token + new Refresh Token issued, old `revoked_at` set; (2) re-presenting the now-revoked token → 401 AND all tokens sharing the same `family_id` are revoked (Testcontainers asserts family-wide revocation); (3) expired token → 401 |

---

### TASK-030 — `POST /auth/logout`

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-029 |
| Spec Refs | REQ-USR-006, Constitution SEC-2; Doc 10 §2.5; Doc 07 §2.3 |
| Acceptance Criteria | `POST /api/v1/auth/logout` with valid Refresh Token body → 204; `refresh_tokens.revoked_at` set for the presented token; subsequent refresh with the same token → 401; Testcontainers IT verifies the revocation |

---

### TASK-031 — Password reset: forgot + reset endpoints + outbox event

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-025, TASK-019 |
| Spec Refs | REQ-USR-007, Constitution SEC-2, SEC-4; Doc 10 §1.1 (all Refresh Tokens revoked on reset); Doc 07 §2.4, §2.5 |
| Acceptance Criteria | `POST /api/v1/auth/forgot-password` returns uniform 202 for both known and unknown emails (no enumeration); `PasswordResetRequestedEvent` written to outbox; `POST /api/v1/auth/reset-password` with valid token → 204, all `refresh_tokens.revoked_at` set for the user, `consumed_at` stamped on reset token; second use of same reset token → 400 |

---

### TASK-032 — UserController: `GET/PUT /users/me`

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-021, TASK-010 |
| Spec Refs | REQ-USR-008, Constitution P4, SEC-3; Doc 07 §2.6, §2.7 |
| Acceptance Criteria | `GET /api/v1/users/me` → 200 with `UserProfileResponse` containing no `passwordHash`; `PUT /api/v1/users/me` with valid fields → 200 updated profile; any `userId` in request body silently ignored (identity from JWT only, AL-5); Testcontainers IT confirms both endpoints |

---

### TASK-033 — `PATCH /users/me/password` (change password)

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-032, TASK-024 |
| Spec Refs | REQ-USR-009, Constitution SEC-1; Doc 10 §1.1; Doc 07 §2.8 |
| Acceptance Criteria | Correct current password + valid new password → 204, all `refresh_tokens.revoked_at` set for the user; wrong current password → 400; new password BCrypt-hashed at cost ≥ 12 (hash prefix `$2a$12$` asserted in DB); plaintext passwords absent from all log lines |

---

### TASK-034 — `DELETE /users/me` + `UserDeletedEvent` via outbox

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-032, TASK-014 |
| Spec Refs | REQ-USR-010, Constitution P4, CQ-8; Doc 10 §4.4; Doc 08 (`UserDeletedEvent`) |
| Acceptance Criteria | `DELETE /api/v1/users/me` → 204; `User.status = DELETED`; all `refresh_tokens.revoked_at` set; `UserDeletedEvent` written to outbox in same `@Transactional`; rollback test: state change and event absent together; subsequent login with deleted credentials → 401 |

---

### TASK-035 — DataExportService: `POST /users/me/data-export` + `GET .../download`

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-032, TASK-020 |
| Spec Refs | REQ-USR-011, Constitution P4, SEC-6; Doc 10 §4.4 |
| Acceptance Criteria | `POST /api/v1/users/me/data-export` → 202; `data_exports.status = 'REQUESTED'`; download endpoint returns 200 for caller, 403 for foreign user; `download_ref` never appears in any log line (SENSITIVE class per Doc 10 §4.2) |

---

### TASK-036 — SecureNotificationDeliveryPort implementation

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-035 |
| Spec Refs | Constitution AL-2 (port adapter), AL-1 (no cross-DB access); Doc 05 §8 (`SecureNotificationDeliveryPort`) |
| Acceptance Criteria | `SecureNotificationDeliveryPort` domain interface and adapter implementation exist; unit test: given a valid `deliveryRef`, port resolves a time-limited URL; adapter does NOT access any other service's database directly (AL-1 asserted by architecture test or code review) |

---

### TASK-037 — Auth rate-limit filter (429 + Retry-After)

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-010, TASK-006 |
| Spec Refs | REQ-SEC-004, Constitution SEC-4; Doc 10 §5.4 |
| Acceptance Criteria | After N failed `POST /auth/login` attempts per IP, subsequent attempt within window → 429 with `Retry-After: <seconds>` integer header; `POST /auth/forgot-password` returns uniform 202 for known and unknown emails; 429 response uses `ErrorResponse` envelope; failed logins logged at `WARN` with `traceId` + masked email (never plaintext) |

---

### TASK-038 — TokenCleanupScheduler

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-028 |
| Spec Refs | Constitution SEC-2; Doc 10 §2.8; Doc 09 §2.3 |
| Acceptance Criteria | Scheduler deletes `refresh_tokens` where `expires_at < now()` AND `revoked_at IS NOT NULL`; unit/integration test: active (non-expired, non-revoked) tokens are untouched; only stale rows removed; scheduler is idempotent on repeated runs |

---

### TASK-039 — Auth integration test suite (Testcontainers, full BDD coverage)

| Field | Value |
|-------|-------|
| Phase | Phase 1 — Identity & Access |
| Depends On | TASK-037, TASK-034 |
| Spec Refs | REQ-USR-003..006, REQ-SEC-001..004, Constitution CQ-6, CQ-7; Doc 04 §2 BDD scenarios |
| Acceptance Criteria | `AuthFlowIT` covers: register → verify → login → access protected resource → refresh → reuse revoked token (entire `family_id` revoked) → logout; all Doc 04 §2 BDD scenario tags (`@happy-path`, `@failure-path`, `@security`, `@boundary`) have a matching IT; BCrypt cost ≥ 12 asserted; no PII in test log output; Phase 1 gate from Doc 12 §3 fully green |

---

## Phase 2 — Core Domains (`category-service` & `expense-service`)

### TASK-040 — Flyway V1: `categories` table (category-service)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-001, TASK-002 |
| Spec Refs | REQ-CAT-001, Constitution DB-3, DB-4, DB-7, DB-8; Doc 09 §3.1 |
| Acceptance Criteria | `V1__create_categories.sql` creates `categories` with all columns per Doc 09 §3.1; `ck_categories_default_no_owner CHECK((origin='DEFAULT' AND user_id IS NULL) OR (origin='CUSTOM' AND user_id IS NOT NULL))`; `uq_categories_owner_name UNIQUE(user_id, name)`; partial index `idx_categories_system_role WHERE system_role='SAVINGS'` exists |

---

### TASK-041 — Category entity + CategoryType / CategoryOrigin / CategorySystemRole enums

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-040 |
| Spec Refs | REQ-CAT-001, Constitution AL-4; Doc 02 Glossary (`Category`, `CategoryType`); Doc 09 §3.1 |
| Acceptance Criteria | `Category.java` JPA entity with all columns; `CategoryType` enum: `EXPENSE`, `INCOME`, `BOTH`; `CategoryOrigin` enum: `DEFAULT`, `CUSTOM`; `CategorySystemRole` enum: `NONE`, `SAVINGS`; entity carries no serialization annotation exposing it to HTTP (AL-4) |

---

### TASK-042 — DefaultCategorySeeder ApplicationRunner

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-041 |
| Spec Refs | REQ-CAT-001; Doc 02 Glossary (Default Category, Savings Category); Doc 07 §3 |
| Acceptance Criteria | On startup, ≥ 11 Default Categories seeded (including `Food`, `Transport`, `Housing`, `Health`); Savings Category has `system_role='SAVINGS'` and `user_id=NULL`; re-running seed is idempotent (no duplicate rows); Testcontainers IT: fresh DB after seed shows all required defaults |

---

### TASK-043 — CategoryAuthoringService + `GET/POST/PUT /categories`

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-041, TASK-007 |
| Spec Refs | REQ-CAT-002, REQ-CAT-003, Constitution CQ-1, CQ-2, API-1, API-4; Doc 07 §3.1, §3.2 |
| Acceptance Criteria | `POST /api/v1/categories` with valid `name`, `type`, optional `icon`, `color` → 201 + `Location`; duplicate name for same `user_id` → 409; `PUT /api/v1/categories/{id}` updates mutable fields; Custom name uniqueness is per-owner only (two users may share a name) |

---

### TASK-044 — Block edit/delete of DEFAULT categories; ownership on Custom

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-043 |
| Spec Refs | REQ-CAT-001, REQ-CAT-003, Constitution SEC-3, P4; Doc 07 §3 (`deletable:false`); Doc 10 §3.5 |
| Acceptance Criteria | `DELETE /api/v1/categories/{id}` on a Default Category → 409; `PUT` on a Default Category → 403; user B attempting `PUT/DELETE` on user A's Custom Category → 403 (never 404); Testcontainers IT asserts all four cases |

---

### TASK-045 — CategoryDeletionGuard via CategoryUsagePort (in-use → 409)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-043, TASK-044 |
| Spec Refs | REQ-CAT-005, Constitution AL-1, AL-2; Doc 09 §3.1 (application-layer enforcement note) |
| Acceptance Criteria | `DELETE /api/v1/categories/{id}` when Category has associated Expenses → 409 with body indicating in-use; check performed via `CategoryUsagePort` interface (not a cross-service DB query, AL-1 asserted); unit test: mock port returns `true` (in use) → service raises `BusinessConflictException` |

---

### TASK-046 — CategoryLookupPort internal endpoint (consumed by expense-service/budget-service)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-043 |
| Spec Refs | Constitution AL-2; REQ-CAT-004; Doc 05 §8 (`CategoryLookupPort`); Doc 09 §3.1 |
| Acceptance Criteria | Internal HTTP endpoint (or adapter contract) exposed by category-service; validates: Category exists + is visible to caller (DEFAULT → all users; CUSTOM → owner only) + `type` compatible with intended use (`EXPENSE`/`BOTH` for Expense creation); unit test: CUSTOM Category owned by user B is invisible to user A → validation fails; `INCOME`-type Category rejected for Expense creation |

---

### TASK-047 — `GET /categories/{id}` + `?type=` filter on list

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-043 |
| Spec Refs | REQ-CAT-004, Constitution API-2 (pagination envelope); Doc 07 §3.1 |
| Acceptance Criteria | `GET /api/v1/categories?type=EXPENSE` returns only Categories with `type IN ('EXPENSE','BOTH')`; `GET /api/v1/categories/{id}` → 200 for caller's own or DEFAULT; foreign Custom → 403; response list includes DEFAULT Categories + caller's CUSTOM Categories; `PageResponse` envelope present |

---

### TASK-048 — Category integration test suite (Testcontainers)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-047, TASK-045 |
| Spec Refs | REQ-CAT-001..005, Constitution CQ-6, CQ-7; Doc 04 §7 BDD scenarios |
| Acceptance Criteria | `CategoryIT` covers: DEFAULT protection (DELETE → 409), in-use Custom delete (409), name uniqueness per owner, `INCOME`-type rejection for Expense cross-service via port, foreign Custom Category access (403 not 404); all Doc 04 §7 BDD scenario tags pass |

---

### TASK-049 — Flyway V1: `expenses` table (expense-service)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-001, TASK-002 |
| Spec Refs | REQ-EXP-001, Constitution DB-4, DB-5, DB-6; Doc 09 §4.1 |
| Acceptance Criteria | `V1__create_expenses.sql`; `amount NUMERIC(19,4) NOT NULL`, `ck_expenses_amount_positive CHECK(amount > 0)`; `payment_method VARCHAR(12) CHECK(payment_method IN ('UPI','CASH','CREDIT_CARD','DEBIT_CARD','NET_BANKING','OTHER'))`; `category_id UUID NOT NULL` (no FK — cross-service ref); `savings_goal_id UUID NULL` (no FK); composite index `idx_expenses_user_date(user_id, expense_date DESC)` exists |

---

### TASK-050 — Flyway V2: `receipts` table (expense-service)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-049 |
| Spec Refs | REQ-EXP-009, Constitution SEC-5; Doc 09 §4.2; EXP-INV-7 |
| Acceptance Criteria | `V2__create_receipts.sql`; `uq_receipts_expense_id UNIQUE(expense_id)` (1:1 per EXP-INV-7); `ck_receipts_size_max CHECK(size_bytes <= 5242880)`; `mime_type CHECK(mime_type IN ('image/jpeg','image/png','image/webp'))`; `storage_ref VARCHAR(512) NOT NULL`; FK `ON DELETE CASCADE` to `expenses` |

---

### TASK-051 — Flyway V3: `tags` + `expense_tags` tables (expense-service)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-049 |
| Spec Refs | REQ-TAG-001, REQ-TAG-002; Doc 09 §4.3, §4.4 |
| Acceptance Criteria | `V3__create_tags.sql`; `uq_tags_owner_name UNIQUE(user_id, name)`; `expense_tags` join table with `PRIMARY KEY(expense_id, tag_id)`, both FKs `ON DELETE CASCADE`; `idx_expense_tags_tag_id` exists (filter Expenses by Tag, REQ-EXP-004); Flyway applies cleanly |

---

### TASK-052 — Flyway V4: `recurring_expenses` + `recurring_expense_tags` tables

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-049 |
| Spec Refs | REQ-REC-001; Doc 09 §4.5, §4.6 |
| Acceptance Criteria | `V4__create_recurring_expenses.sql`; `frequency CHECK(frequency IN ('DAILY','WEEKLY','MONTHLY','YEARLY'))`; `next_run_date DATE NULL`; `generated_count INT NOT NULL DEFAULT 0`; `recurring_expense_tags` join table created; `idx_recurring_expenses_next_run_date` exists (scheduler scan per REQ-REC-003) |

---

### TASK-053 — Expense entity + PaymentMethod enum

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-049 |
| Spec Refs | REQ-EXP-001, Constitution AL-4; Doc 02 Glossary (`Expense`, `PaymentMethod`); Doc 09 §4.1; INV-9 |
| Acceptance Criteria | `Expense.java` JPA entity; `PaymentMethod` enum: `UPI`, `CASH`, `CREDIT_CARD`, `DEBIT_CARD`, `NET_BANKING`, `OTHER`; entity holds `Set<UUID>` for tag IDs (INV-9, not `Set<Tag>`); `savings_goal_id UUID` nullable field (cross-service ref, no `@ManyToOne`); entity not JSON-serializable (AL-4) |

---

### TASK-054 — ExpenseService: `POST /expenses` (create Expense)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-053, TASK-046, TASK-006 |
| Spec Refs | REQ-EXP-001, REQ-EXP-002, Constitution CQ-1, CQ-2, SEC-3, AL-2; Doc 07 §4.1; Doc 04 §3 BDD |
| Acceptance Criteria | 3-Commit Loop; `POST /api/v1/expenses` with valid fields → 201 + `Location`; `amount ≤ 0` → 400; missing required field → 400 with field name in `errors[]`; invisible/foreign `categoryId` → 403; `userId` taken from JWT, never from request body; `category_id` validated via `CategoryLookupPort` (no cross-DB access) |

---

### TASK-055 — `GET /expenses` paginated list with filters + sort

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-054 |
| Spec Refs | REQ-EXP-003, REQ-EXP-004, REQ-EXP-005, Constitution API-2, DB-6; Doc 07 §4.1 |
| Acceptance Criteria | `GET /api/v1/expenses` → `PageResponse` with `content/page/size/totalElements/totalPages`; filters `?startDate=&endDate=&categoryId=&paymentMethod=&tagId=&savingsGoalId=` each narrow results correctly; sort `?sort=date,desc` and `?sort=amount,asc` work; list never returns another user's Expenses (scoped by `user_id` in query — DB-6) |

---

### TASK-056 — `GET/PUT/DELETE /expenses/{id}` (ownership enforcement)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-054, TASK-007 |
| Spec Refs | REQ-EXP-006, REQ-EXP-007, REQ-EXP-008, Constitution SEC-3, P4; Doc 10 §3.3; Doc 04 §8 BDD |
| Acceptance Criteria | Owner access → 200/204; `{id}` exists but belongs to other user → 403 (never 404); non-existent `{id}` → 404; `DELETE /expenses/{id}` removes Expense and cascades Receipt deletion; `PUT /expenses/{id}` updates all mutable fields including optional `savingsGoalId` |

---

### TASK-057 — Emit `ExpenseCreatedEvent` / `ExpenseUpdatedEvent` / `ExpenseDeletedEvent` via outbox

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-054, TASK-056, TASK-014 |
| Spec Refs | Constitution CQ-8 (outbox in same transaction); Doc 08 (Expense events); Doc 09 §7.1 |
| Acceptance Criteria | For each CREATE/UPDATE/DELETE of Expense: outbox row with correct `event_type` inserted in same `@Transactional`; rollback test: if Expense insert fails, outbox row is also absent; event payload contains `eventId`, `eventType`, `userId`, `traceId`, `amount`, `categoryId` per Doc 08 §1.1 |

---

### TASK-058 — ContributionEventsPort (goal-link change events)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-057 |
| Spec Refs | Constitution AL-2; Doc 08 (`ExpenseLinkedToSavingsGoalEvent`, `SavingsGoalContributionAmountAdjustedEvent`, `ExpenseUnlinkedFromSavingsGoalEvent`); Doc 05 §8 (`ContributionEventsPort`); REQ-EXP-007 |
| Acceptance Criteria | `ContributionEventsPort` interface and outbox-backed implementation; when `savingsGoalId` is added/changed/removed, the correct event written to outbox in same `@Transactional`; no direct SQL to `savings_goal_db` (AL-1); unit test: adding `savingsGoalId` to Expense → `ExpenseLinkedToSavingsGoalEvent` in outbox |

---

### TASK-059 — SpendingFeedPort (expense events for budget-service)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-057 |
| Spec Refs | Constitution AL-2; Doc 05 §8 (`SpendingFeedPort`); Doc 08 (expense events for Budget); REQ-BUD-004 |
| Acceptance Criteria | `SpendingFeedPort` interface and outbox-backed implementation; `ExpenseCreatedEvent` payload carries `categoryId`, `amount`, `userId` for Budget matching; no direct SQL to `budget_db` (AL-1); unit test: Expense created → event in outbox with correct payload fields |

---

### TASK-060 — ReceiptService: `POST /expenses/{id}/receipt` (upload, EXIF strip)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-056, TASK-050 |
| Spec Refs | REQ-EXP-009, REQ-SEC-005, Constitution SEC-5; Doc 10 §5.2, §5.3 |
| Acceptance Criteria | 3-Commit Loop; magic-byte sniff: JPEG/PNG/WEBP accepted → 200; PDF → 400; GIF → 400; size > 5 MB → 400; size = 5 MB exactly → 200; EXIF metadata stripped before MinIO write (asserted: metadata-extractor returns 0 EXIF segments on the stored object); storage key is server-generated UUID path (no client filename in path); second upload replaces (1:1, EXP-INV-7); foreign Expense → 403 |

---

### TASK-061 — `GET/DELETE /expenses/{id}/receipt` (secure serve + delete)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-060 |
| Spec Refs | REQ-EXP-010, REQ-EXP-011, Constitution SEC-5; Doc 10 §5.3 |
| Acceptance Criteria | `GET` streams image with `Content-Type` matching stored `mime_type`, `Content-Disposition: inline`, `X-Content-Type-Options: nosniff`; ownership check (foreign → 403); `DELETE` → 204, MinIO object removed, `receipts` row deleted, Expense itself retained; Testcontainers IT covers both endpoints |

---

### TASK-062 — TagManagementService: CRUD + detach-on-delete

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-051, TASK-007 |
| Spec Refs | REQ-TAG-001, REQ-TAG-002, REQ-TAG-003, Constitution SEC-3; Doc 07 §5; Doc 04 §10 BDD |
| Acceptance Criteria | `POST /api/v1/tags` → 201; duplicate name for same user → 409; `DELETE /api/v1/tags/{id}` → 204, all `expense_tags` rows for that Tag removed (Tag detached from Expenses, Expenses NOT deleted); foreign Tag delete → 403 (not 404); `GET /expenses?tagId=` returns correctly filtered Expense list |

---

### TASK-063 — RecurringExpenseService: CRUD with `scope=THIS|THIS_AND_FUTURE`

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-052, TASK-053 |
| Spec Refs | REQ-REC-001, REQ-REC-002, REQ-REC-004, REQ-REC-005, REQ-REC-006; Doc 07 §6; Doc 04 §9 BDD |
| Acceptance Criteria | `POST /api/v1/recurring-expenses` → 201 + Location with `frequency`, `anchor_date`, optional `end_date`/`max_occurrences`; `PUT /{id}?scope=THIS` updates only the target Occurrence Expense (template unchanged); `PUT /{id}?scope=THIS_AND_FUTURE` splits template (original gets `end_date` = day before occurrence, new template created with new amount); DELETE with `scope=THIS_AND_FUTURE` sets `end_date` on template |

---

### TASK-064 — RecurringExpenseGenerator (@Scheduled, next Occurrence creation)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-063, TASK-057 |
| Spec Refs | REQ-REC-003; Doc 08 (`RecurringGenerationFailedEvent`); Doc 09 §4.5 |
| Acceptance Criteria | Scheduler runs; for each `recurring_expenses` where `next_run_date ≤ today` and constraints not exhausted: creates `expenses` row with `recurring_expense_id` set, copies tags to `expense_tags`, advances `next_run_date`, increments `generated_count`; failure → `RecurringGenerationFailedEvent` in outbox (parked for Phase-2 Notification consumer); idempotent on same `next_run_date` (no duplicate Expense for same date) |

---

### TASK-065 — ExpenseImportService: `POST /expenses/import` (CSV, injection-safe)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-054 |
| Spec Refs | REQ-EXP-012, REQ-EXP-013, Constitution API-7, SEC-5 (CSV injection); Doc 10 §5.5 |
| Acceptance Criteria | File > 10 MB → 400; > 10,000 rows → 400; non-`text/csv` MIME → 400; leading formula chars (`=`, `+`, `-`, `@`, `\t`, `\r`) stripped from all cell values; `Idempotency-Key` header: duplicate import returns cached result; per-row `ImportExpensesReport` in response; Savings Goal name matched to caller's own goals only; unmatched goal name → warning in report, Expense still created |

---

### TASK-066 — ExpenseExportService: `GET /expenses/export` (streaming CSV)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-055 |
| Spec Refs | REQ-EXP-014, Constitution API-7, CQ-10 (no full load into memory); Doc 07 §4.2 |
| Acceptance Criteria | `GET /api/v1/expenses/export?startDate=&endDate=` → `Content-Type: text/csv`, streaming response (no full result set buffered into memory); CSV injection protection on all cell values; response contains only caller's Expenses in the date range; 200 on success |

---

### TASK-067 — CategoryLookupPort HTTP adapter (expense-service side)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-046, TASK-054 |
| Spec Refs | Constitution AL-1, AL-2; Doc 05 §8 (`CategoryLookupPort`) |
| Acceptance Criteria | `CategoryLookupHttpAdapter` implements `CategoryLookupPort` domain interface; calls category-service internal endpoint from TASK-046; does NOT access `category_db` directly (AL-1); unit test: mock HTTP client returning not-found → adapter raises `ForbiddenOwnershipException` per spec; no cross-schema SQL present |

---

### TASK-068 — expense-service integration test suite (Testcontainers)

| Field | Value |
|-------|-------|
| Phase | Phase 2 — Core Domains |
| Depends On | TASK-066, TASK-061 |
| Spec Refs | REQ-EXP-001..014, REQ-TAG-001..003, REQ-REC-001..006, REQ-SEC-003..005, Constitution CQ-6, CQ-7; Doc 04 §3, §8, §9, §10 BDD |
| Acceptance Criteria | `ExpenseIT` covers: Receipt rejections (PDF, >5 MB, magic-byte mismatch all → 400), EXIF stripped (0 EXIF segments asserted), CSV injection neutralized, `Idempotency-Key` dedup, foreign Expense → 403, Tag detach preserves Expenses, Recurring split correct; all Doc 04 §3 + §8 + §9 + §10 BDD scenario tags pass; Phase 2 gate from Doc 12 §3 fully green |

---

## Phase 3 — Advanced Domains (`savings-goal-service` & `budget-service`)

### TASK-069 — Flyway V1: `savings_goals` table (savings-goal-service)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-001, TASK-002 |
| Spec Refs | REQ-GOAL-001, Constitution DB-4, DB-5, DB-8; Doc 09 §5.1 |
| Acceptance Criteria | `V1__create_savings_goals.sql`; `status CHECK(status IN ('ACTIVE','PAUSED','COMPLETED','ABANDONED'))`; `total_contributed NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK(total_contributed >= 0)`; `target_amount NUMERIC(19,4) NOT NULL CHECK(target_amount > 0)`; composite index `idx_savings_goals_user_status(user_id, status)` exists |

---

### TASK-070 — Flyway V2: `contribution_entries` table

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-069 |
| Spec Refs | Constitution SG-INV-4; Doc 09 §5.2 |
| Acceptance Criteria | `V2__create_contribution_entries.sql`; `uq_contribution_entries_goal_expense UNIQUE(savings_goal_id, expense_id)` (one entry per backing Expense, SG-INV-4); `expense_id UUID NOT NULL` (no FK — cross-service ref per AL-1); `source CHECK(source IN ('GOAL_SCREEN','LINKED_EXPENSE'))`; FK to `savings_goals(id) ON DELETE CASCADE` |

---

### TASK-071 — SavingsGoal entity + GoalStatus enum + ContributionEntry entity

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-069, TASK-070 |
| Spec Refs | REQ-GOAL-001, Constitution AL-4; Doc 02 Glossary (`SavingsGoal`, `GoalStatus`, `Contribution`); Doc 09 §5.1, §5.2 |
| Acceptance Criteria | `SavingsGoal.java` entity; `GoalStatus` enum: `ACTIVE`, `PAUSED`, `COMPLETED`, `ABANDONED`; `ContributionEntry.java` entity with `expense_id UUID` field (cross-service ref, no `@ManyToOne` to Expense entity); `source` enum: `GOAL_SCREEN`, `LINKED_EXPENSE`; neither entity is JSON-serializable (AL-4) |

---

### TASK-072 — SavingsGoal CRUD: `GET/POST /savings-goals` + `GET/PUT/DELETE /{id}`

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-071, TASK-007 |
| Spec Refs | REQ-GOAL-001, REQ-GOAL-002, REQ-GOAL-003, REQ-GOAL-010, Constitution SEC-3, API-2, API-4; Doc 07 §5.1, §5.2 |
| Acceptance Criteria | `POST /api/v1/savings-goals` → 201 + Location, `status=ACTIVE`, `totalContributed=0`; `PUT /{id}` → 200 updated; `DELETE /{id}` → 204, linked Expenses detached (not deleted), `SavingsGoalDeletedEvent` in outbox; list `?status=ACTIVE` vs `?status=COMPLETED` returns correct split; foreign goal → 403 (not 404) |

---

### TASK-073 — ContributionPort (instructs expense-service to create backing Expense)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-072, TASK-046 |
| Spec Refs | Constitution AL-1, AL-2; REQ-GOAL-004; Doc 05 §8 (`ContributionPort`) |
| Acceptance Criteria | `ContributionPort` domain interface and HTTP adapter; when savings-goal-service needs to create a backing Expense for a Contribution from the goal screen, it calls expense-service via this port with `categoryId` = Savings Category ID; NO direct SQL to `expense_db` (AL-1 asserted); unit test: adapter calls correct expense-service endpoint |

---

### TASK-074 — ContributionService: `POST /savings-goals/{id}/contributions` (goal-screen flow)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-073, TASK-071 |
| Spec Refs | REQ-GOAL-004, REQ-GOAL-006, Constitution CQ-8, SG-INV-4; Doc 04 §4 BDD (Contribution from goal screen) |
| Acceptance Criteria | 3-Commit Loop; `POST /api/v1/savings-goals/{id}/contributions` → 201; backing Expense created under Savings Category via `ContributionPort`; `contribution_entries` row inserted with `source='GOAL_SCREEN'`; `total_contributed` recomputed; `uq_contribution_entries_goal_expense` prevents duplicate entry for same `expense_id`; Contribution appears in both goal history and Expense list |

---

### TASK-075 — `GET /savings-goals/{id}/contributions` history (paginated)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-074 |
| Spec Refs | REQ-GOAL-006, Constitution API-2; Doc 07 §5.3 |
| Acceptance Criteria | `GET /api/v1/savings-goals/{id}/contributions` → `PageResponse` with `content/page/size/totalElements/totalPages`; each entry exposes `amount`, `currency`, `entryDate`, `source`; foreign goal → 403; list shows only the caller's Contributions |

---

### TASK-076 — ExpenseEventConsumer in savings-goal-service (secondary Contribution flow)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-074, TASK-097 |
| Spec Refs | REQ-GOAL-005, REQ-GOAL-006, Constitution CQ-8; Doc 08 (`ExpenseLinkedToSavingsGoalEvent`) |
| Acceptance Criteria | Consumer receives `ExpenseLinkedToSavingsGoalEvent`; inserts `contribution_entries` row with `source='LINKED_EXPENSE'`; recomputes `total_contributed`; idempotent on `event_id` (duplicate delivery → single insert, no duplicate `contribution_entries`); Testcontainers IT with real Kafka verifies single effect on duplicate delivery |

---

### TASK-077 — ContributionReconciliationService (amount-adjusted / deleted / unlinked)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-076 |
| Spec Refs | REQ-GOAL-007, Constitution CQ-8; Doc 08 (`SavingsGoalContributionAmountAdjustedEvent`, `ExpenseUnlinkedFromSavingsGoalEvent`, `ExpenseDeletedEvent`); Doc 04 §4 BDD |
| Acceptance Criteria | On `SavingsGoalContributionAmountAdjustedEvent`: updates `contribution_entries.amount`, recomputes `total_contributed`; on `ExpenseUnlinkedFromSavingsGoalEvent` / `ExpenseDeletedEvent`: removes `contribution_entries` row, recomputes total; idempotent via processed_events guard; Testcontainers IT: edit backing Expense amount → goal total updates correctly |

---

### TASK-078 — Auto-complete on `total_contributed ≥ target_amount` + `SavingsGoalCompletedEvent`

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-077, TASK-014 |
| Spec Refs | REQ-GOAL-011, Constitution SG-INV-6, CQ-8; Doc 08 (`SavingsGoalCompletedEvent`); Doc 04 §4 BDD (goal completion) |
| Acceptance Criteria | When `total_contributed ≥ target_amount`: `SavingsGoal.status` transitions to `COMPLETED` and `SavingsGoalCompletedEvent` written to outbox in same `@Transactional`; transition fires exactly ONCE per goal (idempotent — already COMPLETED → no second event); event parked (no Phase-1 consumer) |

---

### TASK-079 — `PATCH /savings-goals/{id}/status` state machine (illegal → 409)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-072 |
| Spec Refs | REQ-GOAL-012, REQ-GOAL-013; Doc 04 §11 BDD (lifecycle); Doc 06 §3 (goal invariants) |
| Acceptance Criteria | Valid transitions succeed (ACTIVE→PAUSED, ACTIVE→COMPLETED, ACTIVE→ABANDONED, PAUSED→ACTIVE); invalid transition (e.g. COMPLETED→ACTIVE) → 409; PAUSED goal excluded from `?status=ACTIVE` list; Contribution History preserved on all status transitions |

---

### TASK-080 — GoalProjectionService (`remainingAmount`, `projectedCompletionDate`)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-079, TASK-075 |
| Spec Refs | REQ-GOAL-008, REQ-GOAL-009; Doc 07 §5.4 |
| Acceptance Criteria | Goal detail response includes `remainingAmount` (`target_amount - total_contributed`), `percentageAchieved`, `projectedCompletionDate` (based on average monthly Contribution rate from history); PAUSED and COMPLETED goals return `projectedCompletionDate: null`; unit test with known Contribution history asserts correct projection |

---

### TASK-081 — `SavingsGoalDeletedEvent` + expense-service detaches linked Expenses

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-072, TASK-014 |
| Spec Refs | REQ-GOAL-003, Constitution CQ-8; Doc 08 (`SavingsGoalDeletedEvent`); Doc 04 §4 BDD (deleting goal retains Expenses) |
| Acceptance Criteria | `DELETE /savings-goals/{id}` → `SavingsGoalDeletedEvent` written to outbox in same tx; expense-service consumer sets `expenses.savings_goal_id = NULL` for all linked Expenses; linked Expenses NOT deleted; integration test: goal deleted → linked Expenses still exist as regular Expenses without goal association |

---

### TASK-082 — savings-goal-service integration test suite (Testcontainers)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-081, TASK-078 |
| Spec Refs | REQ-GOAL-001..013, Constitution CQ-6, CQ-7; Doc 04 §4, §11 BDD |
| Acceptance Criteria | `SavingsGoalIT` covers: Contribution from goal screen + linked Expense flow, reconciliation on edit/delete/unlink (total updates correctly), auto-complete fires exactly once, illegal status transition → 409, foreign goal → 403, Expenses retained after goal delete; all Doc 04 §4 + §11 BDD scenario tags pass |

---

### TASK-083 — Flyway V1: `budgets` table (budget-service)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-001, TASK-002 |
| Spec Refs | REQ-BUD-001, Constitution DB-4, DB-5; Doc 09 §6.1 |
| Acceptance Criteria | `V1__create_budgets.sql`; `scope CHECK(scope IN ('OVERALL','CATEGORY'))`; `ck_budgets_scope_category CHECK((scope='CATEGORY' AND category_id IS NOT NULL) OR (scope='OVERALL' AND category_id IS NULL))`; `budget_limit NUMERIC(19,4) NOT NULL CHECK(budget_limit > 0)`; `period_type CHECK(period_type IN ('WEEKLY','MONTHLY'))`; partial index `idx_budgets_active WHERE active = true` |

---

### TASK-084 — Flyway V2: `budget_period_ledgers` table

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-083 |
| Spec Refs | Constitution BUD-INV-5; Doc 09 §6.2 |
| Acceptance Criteria | `V2__create_budget_period_ledgers.sql`; `fired_eighty_percent BOOLEAN NOT NULL DEFAULT false`; `fired_exceeded BOOLEAN NOT NULL DEFAULT false`; `uq_budget_period_ledgers_budget_window UNIQUE(budget_id, period_start)` (one ledger per Budget Period); composite index `idx_budget_period_ledgers_budget_period(budget_id, period_start, period_end)` exists |

---

### TASK-085 — Budget entity + BudgetPeriodLedger entity + BudgetScope / BudgetPeriodType enums

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-083, TASK-084 |
| Spec Refs | REQ-BUD-001, Constitution AL-4; Doc 02 Glossary (`Budget`, `BudgetPeriodType`); Doc 09 §6.1, §6.2 |
| Acceptance Criteria | `Budget.java` entity; `BudgetPeriodLedger.java` entity with `firedEightyPercent` and `firedExceeded` boolean fields; `BudgetScope` enum: `OVERALL`, `CATEGORY`; `BudgetPeriodType` enum: `WEEKLY`, `MONTHLY`; neither entity is JSON-serializable (AL-4) |

---

### TASK-086 — BudgetAuthoringService: CRUD + CategoryLookupPort for CATEGORY scope

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-085, TASK-046 |
| Spec Refs | REQ-BUD-001, Constitution CQ-1, CQ-2, AL-2, SEC-3; Doc 07 §6.1, §6.2; Doc 04 §13 BDD |
| Acceptance Criteria | `POST /api/v1/budgets` with `scope=CATEGORY` + `categoryId` → 201; `scope=OVERALL` without `categoryId` → 201; `budget_limit = 0` → 400 (BUD-INV-1); `categoryId` validated via `CategoryLookupPort` (no cross-DB, AL-2); foreign Budget → 403; list → `PageResponse` scoped to caller's Budgets |

---

### TASK-087 — `PATCH /budgets/{id}/activation` (deactivate/reactivate)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-086 |
| Spec Refs | REQ-BUD-002, Constitution BUD-INV-7 (deactivated Budget never alerts); Doc 07 §6.3 |
| Acceptance Criteria | `PATCH /api/v1/budgets/{id}/activation` with `{"active":false}` → 200, `budgets.active = false`; deactivated Budget does not trigger Budget Alert on subsequent Expense events (asserted in integration test); reactivation → Budget Alert evaluation resumes |

---

### TASK-088 — `PATCH /budgets/{id}/rollover` toggle

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-086 |
| Spec Refs | REQ-BUD-003; Doc 07 §6.4 |
| Acceptance Criteria | `PATCH /api/v1/budgets/{id}/rollover` with `{"rolloverEnabled":true}` → 200; `budgets.rollover_enabled = true`; integration test: with rollover enabled, at period close, new `budget_period_ledgers.carried_in` = previous period's unspent; without rollover, `carried_in = 0` |

---

### TASK-089 — ExpenseEventConsumer in budget-service (idempotent `spent` recompute)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-085, TASK-097, TASK-059 |
| Spec Refs | REQ-BUD-004, REQ-BUD-005, Constitution CQ-8, BUD-INV-4; Doc 08 (`ExpenseCreatedEvent`, `ExpenseUpdatedEvent`, `ExpenseDeletedEvent`) |
| Acceptance Criteria | Consumer processes `ExpenseCreatedEvent`; matches active Budget(s) by `userId` + `categoryId` (CATEGORY scope) or `userId` (OVERALL scope); updates `budget_period_ledgers.spent` atomically; idempotent via processed_events guard (duplicate `event_id` → single effect); unit test: sequence of create/update/delete events produces correct final `spent` |

---

### TASK-090 — BudgetEvaluationService (fire 80% / exceeded once per Budget Period per threshold)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-089, TASK-014 |
| Spec Refs | REQ-BUD-004, REQ-BUD-005, REQ-BUD-006, Constitution BUD-INV-5, CQ-8; Doc 08 (`BudgetAlertFiredEvent`); Doc 04 §5 BDD |
| Acceptance Criteria | When `spent/budget_limit ≥ 0.80` and `fired_eighty_percent = false`: set flag `true`, emit `BudgetAlertFiredEvent(type=EIGHTY_PERCENT)` in outbox, all in same `@Transactional`; when `spent/budget_limit ≥ 1.00` and `fired_exceeded = false`: emit `BudgetAlertFiredEvent(type=EXCEEDED)`; repeated events when flag already `true` → NO additional event; deactivated Budget (`active=false`) → no event ever |

---

### TASK-091 — BudgetRolloverService + scheduler (idempotent period close + open)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-088, TASK-084 |
| Spec Refs | REQ-BUD-003, Constitution BUD-INV-8, CQ-8; Doc 09 §6.2 |
| Acceptance Criteria | Scheduler detects period end; closes current ledger; opens new `budget_period_ledgers` row with `period_start = new period start`, `carried_in = previous unspent` if `rollover_enabled = true` else `0`, `fired_eighty_percent = false`, `fired_exceeded = false`; `uq_budget_period_ledgers_budget_window` prevents duplicate ledger for same period; idempotent on re-run |

---

### TASK-092 — BudgetStatusService: `GET /budgets/{id}` with derived fields

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-089, TASK-090 |
| Spec Refs | REQ-BUD-007; Doc 07 §6.2; Doc 04 §5 BDD (Budget status view) |
| Acceptance Criteria | `GET /api/v1/budgets/{id}` → `BudgetStatusResponse` with `budgetLimit`, `spent`, `remaining`, `percentUsed`, `firedThresholds` (list of fired threshold types for current period), `carriedIn`; all Money fields serialized as `MoneyDto {amount, currency:"INR"}`; foreign Budget → 403 |

---

### TASK-093 — budget-service integration test suite (Testcontainers)

| Field | Value |
|-------|-------|
| Phase | Phase 3 — Advanced Domains |
| Depends On | TASK-092, TASK-091 |
| Spec Refs | REQ-BUD-001..007, Constitution CQ-6, CQ-7; Doc 04 §5, §13 BDD |
| Acceptance Criteria | `BudgetIT` covers: repeated Expense events fire 80% alert exactly once, exceeded alert exactly once; deactivated Budget fires no alert; rollover carries unspent only when enabled; threshold counters reset at new period; foreign Budget → 403 (not 404); all Doc 04 §5 + §13 BDD scenario tags pass; Phase 3 gate from Doc 12 §3 fully green |

---

## Phase 4 — Event-Driven Infrastructure (Transactional Outbox)

### TASK-094 — Per-service `outbox` table Flyway migrations (all 5 services)

| Field | Value |
|-------|-------|
| Phase | Phase 4 — Event-Driven Infrastructure |
| Depends On | TASK-014 |
| Spec Refs | Constitution CQ-8; Doc 08 §1.3; Doc 09 §7.1 |
| Acceptance Criteria | Each of the 5 services has a Flyway migration creating `outbox` per Doc 09 §7.1 schema; `uq_outbox_event_id UNIQUE(event_id)` on each; `idx_outbox_published_created ON (published, created_at)` on each; no shared `outbox` table across services (AL-1 — each migration is in the service's own migration directory) |

---

### TASK-095 — OutboxWriter (write event in same @Transactional as state change)

| Field | Value |
|-------|-------|
| Phase | Phase 4 — Event-Driven Infrastructure |
| Depends On | TASK-094 |
| Spec Refs | Constitution CQ-8; Doc 08 §1.3 |
| Acceptance Criteria | `OutboxWriter` Spring component; `write(EventEnvelope)` inserts row into the local service's `outbox` table within the calling `@Transactional`; rollback test: if the enclosing business transaction rolls back, the outbox row is also absent (outbox row count unchanged); `event_id` populated from `EventEnvelope.eventId` (UUID) |

---

### TASK-096 — OutboxRelayScheduler (poll → publish to Kafka → mark published)

| Field | Value |
|-------|-------|
| Phase | Phase 4 — Event-Driven Infrastructure |
| Depends On | TASK-095, TASK-002 |
| Spec Refs | Constitution CQ-8; Doc 08 §1.3 |
| Acceptance Criteria | Scheduler polls `outbox WHERE published = false ORDER BY created_at` (using `idx_outbox_published_created`); publishes each serialized `EventEnvelope` to the Kafka topic; sets `published = true` and `published_at = now()`; already-published rows skipped on re-poll; Testcontainers IT: outbox row written → relay runs → message appears on Kafka topic |

---

### TASK-097 — `processed_events` table + idempotent-consume guard (all consuming services)

| Field | Value |
|-------|-------|
| Phase | Phase 4 — Event-Driven Infrastructure |
| Depends On | TASK-094 |
| Spec Refs | Constitution CQ-8; Doc 08 §1.3; Doc 09 §7.2 |
| Acceptance Criteria | Each consuming service (`savings-goal-service`, `budget-service`) has a Flyway migration creating `processed_events(event_id UUID PRIMARY KEY, event_type VARCHAR(200) NOT NULL, processed_at TIMESTAMPTZ NOT NULL DEFAULT now())`; `ProcessedEventGuard` inserts `event_id` before processing; duplicate insert (same `event_id`) → unique constraint violation → event skipped; Testcontainers IT: duplicate Kafka delivery of same event produces exactly one effect |

---

### TASK-098 — Standard `EventEnvelope` in shared-kernel

| Field | Value |
|-------|-------|
| Phase | Phase 4 — Event-Driven Infrastructure |
| Depends On | TASK-001 |
| Spec Refs | Constitution CQ-8; Doc 08 §1.1 (event envelope schema) |
| Acceptance Criteria | `EventEnvelope.java` in `shared-kernel`; fields: `eventId UUID`, `eventType String`, `eventVersion String`, `occurredAt Instant`, `producer String`, `userId UUID`, `traceId UUID`, `payload Object`; unit test: serialize → deserialize round-trip produces identical values for all 8 fields; no field missing from JSON output |

---

### TASK-099 — Event-flow integration test (expense → budget + goal, real Kafka)

| Field | Value |
|-------|-------|
| Phase | Phase 4 — Event-Driven Infrastructure |
| Depends On | TASK-096, TASK-097 |
| Spec Refs | Constitution CQ-8, AL-1; Doc 08; REQ-BUD-004, REQ-GOAL-005 |
| Acceptance Criteria | `EventFlowIT` using Testcontainers (real Kafka + PostgreSQL×3): Expense created in expense-service → `ExpenseCreatedEvent` on Kafka → budget-service `budget_period_ledgers.spent` updated AND savings-goal-service `total_contributed` updated; duplicate delivery of same event → single effect (idempotency guard); no cross-schema SQL detectable during test execution (AL-1); Phase 4 gate from Doc 12 §3 fully green |

---

## Phase 5 — Frontend Integration

### TASK-100 — Vite + React 18 + TypeScript strict scaffold

| Field | Value |
|-------|-------|
| Phase | Phase 5 — Frontend Integration |
| Depends On | TASK-001 |
| Spec Refs | Constitution P2, FE-3; Doc 12 §1.1 frontend structure |
| Acceptance Criteria | `frontend/tsconfig.json` with `"strict": true`; `tsc --noEmit` produces 0 errors on the scaffold; `vite build` succeeds; no `any` type in any scaffold `.ts`/`.tsx` file (lint gate); `package.json` declares React 18 and TypeScript |

---

### TASK-101 — apiConfig (env-based base URLs, no hardcoded strings)

| Field | Value |
|-------|-------|
| Phase | Phase 5 — Frontend Integration |
| Depends On | TASK-100 |
| Spec Refs | Constitution FE-6; Doc 11 §4.2 |
| Acceptance Criteria | `apiConfig.ts` reads base URL from `import.meta.env.VITE_API_BASE_URL`; grep/lint rule confirms no hardcoded URL string (e.g. `http://localhost`) in any `.ts`/`.tsx` file; `tsc` strict clean |

---

### TASK-102 — Single axiosClient + single-flight refresh interceptor

| Field | Value |
|-------|-------|
| Phase | Phase 5 — Frontend Integration |
| Depends On | TASK-101 |
| Spec Refs | Constitution FE-1, FE-2; Doc 10 §2.6; Doc 11 §4.2 |
| Acceptance Criteria | Exactly one Axios instance exported from `axiosClient.ts`; 401 interceptor queues concurrent 401s behind a single in-flight refresh call (refresh mutex — no parallel refresh attempts); on refresh success: all queued requests replayed; on refresh 401: tokens cleared, redirect to login; Vitest test with MSW confirms exactly one `POST /auth/refresh` call when two concurrent 401 responses arrive |

---

### TASK-103 — Auth store (in-memory Access Token) + ProtectedRoute guard

| Field | Value |
|-------|-------|
| Phase | Phase 5 — Frontend Integration |
| Depends On | TASK-102 |
| Spec Refs | Constitution FE-2; Doc 10 §2.7 |
| Acceptance Criteria | Access Token stored in memory (not `localStorage` or `sessionStorage`); `ProtectedRoute` redirects to login when no valid token present; auth store exports `setTokens`, `clearTokens`, `getAccessToken`; Vitest test: after `clearTokens()`, `getAccessToken()` returns `null` or `undefined` |

---

### TASK-104 — Auth pages (login / register / verify-email / forgot-password / reset-password)

| Field | Value |
|-------|-------|
| Phase | Phase 5 — Frontend Integration |
| Depends On | TASK-103 |
| Spec Refs | REQ-USR-003..007, Constitution FE-4 (loading/error/empty states), FE-5 (client-side validation); Doc 04 §2, §12 BDD |
| Acceptance Criteria | Each page renders: loading state (spinner visible on submit), error state (API error message shown), success state (redirect or confirmation); all forms validate required fields client-side before submit; `tsc` strict clean; RTL tests cover loading/error/success states for login and register pages |

---

### TASK-105 — Shared UI components (LoadingState / ErrorState / EmptyState / PaginatedTable / MoneyDisplay / DateDisplay)

| Field | Value |
|-------|-------|
| Phase | Phase 5 — Frontend Integration |
| Depends On | TASK-100 |
| Spec Refs | Constitution FE-4, FE-7; API-2 (`PageResponse`); Doc 07 §1.3; Doc 15 §3 (approved library registry), §5 (component inventory) |
| Acceptance Criteria | `LoadingState`, `ErrorState`, `EmptyState` render correct fallback UI using shadcn Skeleton/Alert/Card primitives from `src/components/ui/`; `PaginatedTable` built with `@tanstack/react-table` + shadcn Table — accepts `PageResponse<T>` prop and renders rows + pagination controls; `MoneyDisplay` renders `{amount, currency}` as `₹{amount}` via `Intl.NumberFormat('en-IN',{style:'currency',currency:'INR'})` (no extra library); `DateDisplay` formats dates via `date-fns` `enIN` locale; icons from `lucide-react` only; all imports present in Doc 15 §3 registry (no unregistered package); RTL tests for all 6 components; `tsc` strict (no `any`) |

---

### TASK-106 — Categories feature UI (list / form / Default vs Custom)

| Field | Value |
|-------|-------|
| Phase | Phase 5 — Frontend Integration |
| Depends On | TASK-105, TASK-043 |
| Spec Refs | REQ-CAT-001..005, Constitution FE-4; Doc 04 §7 BDD |
| Acceptance Criteria | Category list shows Default + Custom Categories; Default Categories display no delete button (or disabled) — `deletable: false` from API; Create/Edit form for Custom Category; `?type=` filter works in UI; RTL test: delete button absent for DEFAULT Category; `tsc` strict clean |

---

### TASK-107 — Expenses feature UI (list / filters / form / Receipts / Tags / Recurring / import / export)

| Field | Value |
|-------|-------|
| Phase | Phase 5 — Frontend Integration |
| Depends On | TASK-105, TASK-054, TASK-055 |
| Spec Refs | REQ-EXP-001..014, REQ-TAG-001..003, REQ-REC-001..006, Constitution FE-4, FE-5; Doc 04 §3, §8, §9, §10 BDD |
| Acceptance Criteria | Expense list renders loading/error/empty states; filters (date range, Category, PaymentMethod, Tag) update list correctly; create/edit form validates required fields (amount, date, category, PaymentMethod) client-side; Receipt upload shows client-side warning for >5 MB or non-JPEG/PNG/WEBP before submitting; CSV import and export controls present; `tsc` strict clean; RTL tests for loading/error/empty states |

---

### TASK-108 — Savings Goals feature UI (list / detail / progress / projection / Contribution history / form)

| Field | Value |
|-------|-------|
| Phase | Phase 5 — Frontend Integration |
| Depends On | TASK-105, TASK-072, TASK-075 |
| Spec Refs | REQ-GOAL-001..013, Constitution FE-4, FE-5; Doc 04 §4, §11 BDD |
| Acceptance Criteria | List shows ACTIVE and COMPLETED sections separately per `?status=` API; detail shows `remainingAmount`, `percentageAchieved`, `projectedCompletionDate`; Contribution history paginated; Contribution form validates `amount > 0` and date required; goal status control (PAUSED/ABANDONED/COMPLETED); RTL tests for loading/error/empty states; `tsc` strict clean |

---

### TASK-109 — Budgets feature UI (list / status cards / form / activation / rollover toggles)

| Field | Value |
|-------|-------|
| Phase | Phase 5 — Frontend Integration |
| Depends On | TASK-105, TASK-086, TASK-092 |
| Spec Refs | REQ-BUD-001..007, Constitution FE-4, FE-5; Doc 04 §5, §13 BDD |
| Acceptance Criteria | Budget list shows active Budgets with status card (`budgetLimit` / `spent` / `remaining` / `percentUsed`); create/edit form validates `budgetLimit > 0`; activation toggle calls `PATCH /{id}/activation`; rollover toggle calls `PATCH /{id}/rollover`; loading/error/empty states on list and per card; RTL tests; `tsc` strict clean |

---

### TASK-110 — Accessibility audit + responsive breakpoints

| Field | Value |
|-------|-------|
| Phase | Phase 5 — Frontend Integration |
| Depends On | TASK-107, TASK-108, TASK-109 |
| Spec Refs | Constitution FE-4; Doc 12 Phase 5 gate |
| Acceptance Criteria | All interactive elements have ARIA labels or roles; keyboard navigation (Tab/Enter/Escape) works for all modals and forms; color contrast meets WCAG AA on automated audit; UI renders correctly at ≥ 320 px, 768 px, and 1024 px breakpoints (visual check or snapshot test) |

---

### TASK-111 — Frontend test suite (Vitest + RTL + MSW)

| Field | Value |
|-------|-------|
| Phase | Phase 5 — Frontend Integration |
| Depends On | TASK-110, TASK-102 |
| Spec Refs | Constitution FE-4, P6 (Untested Code Does Not Exist), CQ-5; Doc 12 Phase 5 gate |
| Acceptance Criteria | `vitest run` passes 100%; refresh interceptor test: 401 → single `POST /auth/refresh` → original request replayed (MSW confirms); every data-fetching component has RTL tests for loading/error/empty states; no `any` type in any test file; `tsc --noEmit` clean across all `__tests__` directories; Phase 5 gate from Doc 12 §3 fully green |

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 111 |
| Phase 0 tasks | TASK-001 … TASK-015 (15) |
| Phase 1 tasks | TASK-016 … TASK-039 (24) |
| Phase 2 tasks | TASK-040 … TASK-068 (29) |
| Phase 3 tasks | TASK-069 … TASK-093 (25) |
| Phase 4 tasks | TASK-094 … TASK-099 (6) |
| Phase 5 tasks | TASK-100 … TASK-111 (12) |
| Files modified | None (new file only) |
| File path | `specs/001-daily-expense-tracker/13-task-breakdown.md` |

*End of `13-task-breakdown.md` — Daily Expense Application atomic task breakdown.*

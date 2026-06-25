<!--
SYNC IMPACT REPORT
==================
Version change: 1.0.0 → 1.1.0
Bump rationale (MINOR): Full restructure into the enterprise "Engineering Law" format.
  No principle removed or weakened; guidance materially EXPANDED — added Document Metadata,
  Purpose & Scope (bounded contexts), Technology Stack table, Naming Conventions table, and a
  mandatory "Violation:" clause on every law. Underlying rules are preserved and hardened.

Sections (this version, enforced order):
  1. SYNC IMPACT REPORT (this HTML comment)
  2. Document Metadata
  3. Purpose & Scope
  4. The Non-Negotiable Principles
  5. Technology Stack
  6. Architecture Laws
  7. Naming Conventions
  8. API Standards
  9. Security Standards
  10. Code Quality & Database Laws
  11. Frontend Standards
  12. Governance

Mapping from v1.0.0 principles:
  I  Architecture/Stack  → §5 Technology Stack + §6 Architecture Laws + §4.1
  II Contract-First API  → §8 API Standards + §4.4
  III Security           → §9 Security Standards + §4.2
  IV Layering/Quality    → §6 Architecture Laws + §10 Code Quality & Database Laws + §4.3
  V  Testing             → §10 Code Quality & Database Laws + §4.5
  VI Observability       → §10 Code Quality & Database Laws + §4.6
  VII Data/Frontend      → §10 (Database) + §11 Frontend Standards

Templates / artifacts:
  ✅ .specify/templates/plan-template.md   — Constitution Check gate backed by these laws; no edit.
  ✅ .specify/templates/spec-template.md   — no new mandatory spec sections introduced.
  ✅ .specify/templates/tasks-template.md  — testing/observability/security/error categories align.
  ✅ .specify/templates/checklist-template.md — no change required.
  ⚠ README.md / docs/quickstart.md — absent from repo; create consistent with this law if introduced.

Follow-up TODOs: none.
-->

# Daily Expense Application — Engineering Constitution

## 1. Document Metadata

| Field | Value |
|-------|-------|
| **Title** | Daily Expense Application — Engineering Constitution ("The Law") |
| **Version** | 1.1.0 |
| **Status** | ACTIVE — RATIFIED |
| **Ratified** | 2026-06-24 |
| **Last Amended** | 2026-06-24 |
| **Authority** | Supersedes all team conventions, tribal knowledge, and undocumented practice. |
| **Amendment Process** | Amendments are made ONLY via pull request that states the change, its rationale, and migration impact, and is approved by the maintainers. Versioning is semantic: MAJOR = principle removal/redefinition; MINOR = new law/section or expanded guidance; PATCH = clarification. **Violation:** Any change to this document merged without an approving PR and a version bump is reverted on sight. |

## 2. Purpose & Scope

This document is the supreme engineering authority for the Daily Expense Application. It binds
every service, every line of frontend code, and every contributor. It governs the following
**bounded contexts**, each owned by an independently deployable microservice:

| Bounded Context | Owns |
|-----------------|------|
| **Identity & Access** | Registration, email verification, login/logout, password reset/change, JWT issuance & refresh-token rotation, profile, account deletion & data export. |
| **Category** | Default (non-deletable) and custom categories; category typing (Expense/Income/Both). |
| **Expense / Transaction** | Expenses, receipts, tags-on-expense, CSV import/export, recurring expense generation. |
| **Savings Goal** | Goals, contributions (both flows), progress/projection, goal lifecycle (active/paused/completed/abandoned). |
| **Income** | Income entries and recurring income. |
| **Budget** | Per-category/overall budgets, rollover, threshold breach detection. |
| **Reporting & Analytics** | Dashboard aggregates, monthly/yearly/custom reports, PDF/CSV export. |
| **Notification** | In-app notifications, budget/digest emails, recurring-generation failure alerts. |

**In scope:** All backend services, the React frontend, their contracts, data stores, tests,
and operational concerns. **Out of scope:** Nothing within the application boundary may claim
exemption. **Violation:** Work that lands outside a defined bounded context without an amendment
introducing that context is rejected in review.

## 3. The Non-Negotiable Principles

These are the top-level laws. Every other section refines them; none may contradict them.

- **P1 — Contract is Truth.** OpenAPI is the single source of truth for every contract.
  **Violation:** Any client or server that diverges from the published spec is a defect; PR blocked by CI contract check.
- **P2 — Type Safety is Absolute.** Backend uses Java; frontend uses TypeScript strict mode. The `any` type is PROHIBITED. **Violation:** PR blocked by CI; lint/`tsc` failure is non-overridable.
- **P3 — Layers are Sacred.** Controllers, Services, and Repositories never blur. **Violation:** PR blocked in review; merge prohibited.
- **P4 — Data Belongs to its Owner.** A user MUST NEVER read or mutate another user's data; ownership checks are mandatory and return 403. **Violation:** Treated as a security incident; release blocked.
- **P5 — Secrets Never Touch the Repo.** All credentials load from environment/secret store. **Violation:** Commit reverted, secret rotated, incident logged.
- **P6 — Untested Code Does Not Exist.** Every service has mocked unit tests; every endpoint has a Testcontainers integration test. **Violation:** PR blocked by CI coverage/test gates.
- **P7 — Every Request is Traceable.** Each request carries a `traceId` propagated via MDC. **Violation:** PR blocked in review; observability gate fails.

## 4. Technology Stack

The following stack is mandatory and exhaustive. Introducing an alternative requires a
constitutional amendment.

| Layer | Mandated Technology | Hard Constraint | Violation |
|-------|---------------------|-----------------|-----------|
| **Backend Language** | Java | Latest team-agreed LTS; no other JVM language for production services. | PR blocked by CI build matrix. |
| **Backend Framework** | Spring Boot | Microservices architecture; one service per bounded context (§2). | Monolith/merged-context PR rejected in review. |
| **Primary Database** | PostgreSQL | Every service owns its own schema/database. | Shared-DB or cross-schema access PR rejected. |
| **API Contract** | OpenAPI | Single source of truth (P1); spec authored/generated before consumers depend on it. | CI contract diff fails the build. |
| **Frontend Library** | React | Component-based; no second UI framework. | PR rejected in review. |
| **Frontend Language** | TypeScript | `strict: true`; `any` PROHIBITED (P2). | `tsc`/lint failure blocks PR. |
| **HTTP Client (FE)** | Axios | Exactly one shared instance (§11). | `fetch`/second-instance PR blocked by lint + review. |
| **Auth Token** | JWT | 15-min access tokens + rotating 7-day refresh tokens (§9). | Security gate fails; release blocked. |
| **Integration Testing** | Testcontainers | Real app + real PostgreSQL per endpoint (§10). | CI test gate fails. |
| **Observability** | Spring Boot Actuator + MDC | `/actuator/health` exposed; `traceId` in MDC. | Observability gate fails. |

## 5. Architecture Laws

- **AL-1 — Service isolation.** A service MUST NOT read or write another service's database,
  schema, or tables. Cross-context data is obtained ONLY through the owning service's API.
  **Violation:** PR rejected in review; offending access removed before merge.
- **AL-2 — Communicate via interfaces.** A service MUST NOT call another service's repository
  directly; inter-service interaction goes through published service interfaces/contracts.
  **Violation:** PR blocked in review.
- **AL-3 — One context, one service.** Each bounded context in §2 maps to exactly one
  independently deployable microservice. **Violation:** Architecture review rejects the design.
- **AL-4 — DTO boundary.** JPA entities NEVER cross a service boundary or appear in a response;
  DTOs are mandatory for all input and output. **Violation:** PR blocked by CI/serialization check.
- **AL-5 — Stateless services.** Services hold no client session state; identity is derived from
  the JWT on every request. **Violation:** PR rejected in review.

## 6. Naming Conventions

All artefacts MUST follow these conventions. Examples are drawn from the Expense domain.

| Artefact | Convention | Concrete Example |
|----------|------------|------------------|
| Microservice | `kebab-case`, suffix `-service` | `expense-service`, `savings-goal-service` |
| Java package | reverse-domain, lowercase | `com.dailyexpense.expense.service` |
| REST resource path | plural, lowercase, versioned | `/api/v1/expenses`, `/api/v1/savings-goals` |
| Controller | `PascalCase` + `Controller` | `ExpenseController` |
| Service interface | `PascalCase` + `Service` | `ExpenseService` |
| Service implementation | interface name + `Impl` | `ExpenseServiceImpl` |
| Repository | `PascalCase` + `Repository` | `ExpenseRepository` |
| JPA entity | singular `PascalCase` | `Expense`, `SavingsGoal` |
| Request DTO | `PascalCase` + `Request` | `CreateExpenseRequest` |
| Response DTO | `PascalCase` + `Response` | `ExpenseResponse` |
| Command object | `PascalCase` + `Command` | `CreateExpenseCommand` |
| Query object | `PascalCase` + `Query` | `ListExpensesQuery` |
| Enum | singular `PascalCase`; values `UPPER_SNAKE_CASE` | `PaymentMethod.CREDIT_CARD` |
| DB table | `snake_case`, plural | `expenses`, `savings_goals` |
| DB column | `snake_case` | `created_at`, `payment_method` |
| Index | `idx_<table>_<column>` | `idx_expenses_category_id` |
| React component | `PascalCase` | `ExpenseList`, `SavingsGoalCard` |
| React hook | `camelCase`, prefix `use` | `useExpenses`, `useAuthToken` |
| TS type/interface | `PascalCase` | `Expense`, `PaginatedResponse<T>` |
| Env variable | `UPPER_SNAKE_CASE` | `JWT_SECRET`, `DB_PASSWORD` |

**Violation:** Any artefact that breaks its naming convention is blocked by lint/review; merge prohibited.

## 7. API Standards

- **API-1 — Versioned from day one.** Every endpoint MUST live under `/api/v1/...`. Unversioned
  endpoints are PROHIBITED. **Violation:** PR blocked by CI route check.
- **API-2 — Pagination envelope.** Every list endpoint MUST return exactly:
  `content`, `page`, `size`, `totalElements`, `totalPages`. **Violation:** Contract test fails; PR blocked.
- **API-3 — Uniform error envelope.** Every error response MUST use exactly:
  `timestamp`, `status`, `error`, `message`, `path`, `traceId`. **Violation:** Contract test fails; PR blocked.
- **API-4 — Correct status codes.** 200 read/update · 201 create (with `Location` header) ·
  204 delete (no body) · 400 validation · 401 unauthenticated · 403 unauthorized ·
  404 not found · 409 business-rule conflict · 500 unexpected. **Violation:** PR rejected in review.
- **API-5 — DTOs only.** All input and output use DTOs; entities are NEVER serialized (AL-4).
  **Violation:** PR blocked by CI.
- **API-6 — OpenAPI is law (P1).** API docs are auto-generated from the spec/implementation; hand-written
  API docs are PROHIBITED. **Violation:** Stale/divergent doc fails CI contract diff.
- **API-7 — Server-side validation.** All input is validated server-side regardless of the client.
  **Violation:** Security/quality gate fails; PR blocked.

## 8. Security Standards

- **SEC-1 — Password hashing.** Passwords MUST be hashed with BCrypt, minimum cost factor 12.
  Plain-text passwords MUST NEVER appear in logs, responses, or the database.
  **Violation:** Release blocked; treated as a security incident.
- **SEC-2 — Token lifecycle.** Access tokens are JWTs with 15-minute expiry; refresh tokens expire
  in 7 days and ROTATE on every refresh — the old refresh token is invalidated immediately.
  **Violation:** Security gate fails; release blocked.
- **SEC-3 — Absolute ownership (P4).** Every endpoint touching user-owned data MUST verify the
  resource belongs to the caller. Accessing another user's resource returns **403 Forbidden, never 404**.
  **Violation:** Security incident; release blocked.
- **SEC-4 — Rate limiting.** Auth endpoints (login, register, forgot-password) MUST be rate-limited.
  **Violation:** PR blocked in review.
- **SEC-5 — Upload validation.** Receipt uploads accept ONLY JPEG, PNG, WEBP, max 5 MB; type and size
  are validated server-side. **Violation:** PR blocked by CI.
- **SEC-6 — Secrets externalized (P5).** DB password, JWT secret, object-store and SMTP credentials
  load ONLY from environment variables / secret store, never hardcoded or committed.
  **Violation:** Commit reverted, secret rotated, incident logged.

## 9. Code Quality & Database Laws

**Layering & code quality**

- **CQ-1 — Strict layering (P3).** Controllers handle ONLY HTTP + DTOs. Services hold ALL business
  logic. Repositories handle ONLY data access. **Violation:** PR blocked in review.
- **CQ-2 — No nulls.** Service methods MUST NEVER return `null`; lookups that may find nothing return
  `Optional<T>`. **Violation:** PR blocked in review/static analysis.
- **CQ-3 — No magic literals.** Status values, enum strings, and error messages are enums/constants,
  never inline literals. **Violation:** PR blocked by lint/review.
- **CQ-4 — Clean main branch.** Unused imports, commented-out code, and TODO comments MUST NOT reach
  `main`. **Violation:** PR blocked by CI lint.

**Testing**

- **CQ-5 — Unit tests.** Every service class has a corresponding unit-test class verifying business
  rules in isolation with mocks. **Violation:** CI coverage gate fails; PR blocked.
- **CQ-6 — Integration tests.** Every API endpoint has an integration test that boots the real
  application against a real PostgreSQL via Testcontainers. **Violation:** CI test gate fails.
- **CQ-7 — Coverage & independence.** Tests cover happy path and key failures (invalid input,
  unauthorized, not-found) and are mutually independent — no test relies on another's state.
  **Violation:** Flaky/dependent tests block the build.

**Database & transactions**

- **CQ-8 — Transactions.** All write operations run in a transaction; multi-query reads use a
  read-only transaction. **Violation:** PR blocked in review.
- **CQ-9 — Auto timestamps.** Every table has `created_at` and `updated_at`, populated automatically.
  **Violation:** Migration rejected in review.
- **CQ-10 — Indexing & streaming.** Any filtered/joined column is indexed; bulk operations (reports,
  exports) use pagination or streaming and NEVER load all rows into memory.
  **Violation:** PR rejected in review/performance gate.

**Observability**

- **CQ-11 — Request logging.** Every incoming HTTP request is logged with method, path, status,
  response time, and a unique `traceId`. **Violation:** Observability gate fails; PR blocked.
- **CQ-12 — MDC propagation (P7).** The `traceId` is propagated via MDC and appears on every log line
  within the request lifecycle. **Violation:** PR blocked in review.
- **CQ-13 — Log hygiene & levels.** Log levels are correct (DEBUG flow / INFO business event / WARN
  recoverable / ERROR failure with stack trace); PII (email, name, amounts) MUST NEVER be logged.
  **Violation:** PR blocked; PII leak treated as an incident.
- **CQ-14 — Health & metrics.** `/actuator/health` is exposed and key business metrics (e.g.,
  expenses created, active users, budget alerts sent, report generation time) are published.
  **Violation:** Release blocked.

## 10. Frontend Standards

- **FE-1 — Single Axios client.** ALL API calls go through exactly one shared Axios instance. No
  component may call `fetch` or instantiate its own Axios client. **Violation:** PR blocked by lint + review.
- **FE-2 — Transparent token refresh.** The shared client refreshes expired access tokens and retries
  the original request automatically, invisibly to the calling component. **Violation:** PR rejected in review.
- **FE-3 — Strict Mode, no `any` (P2).** TypeScript `strict` mode is enabled and the `any` type is
  PROHIBITED. **Violation:** `tsc`/lint failure blocks PR.
- **FE-4 — Explicit states.** Every data-fetching component explicitly handles loading, error, and
  empty states; a component MUST NEVER render with undefined data. **Violation:** PR blocked in review.
- **FE-5 — Client-side validation.** All forms validate input client-side before submitting (server-side
  validation per API-7 still applies). **Violation:** PR rejected in review.
- **FE-6 — No hardcoded config.** API base URLs and environment-specific values come ONLY from
  environment variables; hardcoding in components is PROHIBITED. **Violation:** PR blocked by lint/review.

## Governance

This constitution supersedes all other practices; where any guideline conflicts with this document,
this document prevails.

- **Compliance review.** Every plan, pull request, and code review MUST verify adherence to every
  applicable law. The `/speckit-plan` Constitution Check gate enforces this before Phase 0 and after
  Phase 1. **Violation:** Non-compliant design or PR is blocked.
- **Release blockers.** Any violation of P4 (ownership), §8 Security, or §9 testing/observability gates
  blocks release outright — no override.
- **Amendments.** Per §1, amendments require an approving PR with rationale, migration impact, and a
  semantic version bump; this Sync Impact Report is updated in the same change.
- **Runtime guidance.** Agent and contributor guidance files MUST remain consistent with this document
  and are updated in the same change that amends a law.

**Version**: 1.1.0 | **Ratified**: 2026-06-24 | **Last Amended**: 2026-06-24

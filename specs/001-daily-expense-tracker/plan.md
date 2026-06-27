# Implementation Plan: Daily Expense Application (Phase 1)

**Branch**: `001-daily-expense-tracker` | **Date**: 2026-06-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification manifest from `/specs/001-daily-expense-tracker/spec.md` (entry point) plus the 15 authoritative sources it lists in Section 6.

> **Authority chain.** spec.md is a manifest; binding detail lives in the numbered sources. This plan
> was authored after reading, in order: the Constitution v1.1.2, `01`–`11` specs, `12-implementation-plan.md`,
> `13-task-breakdown.md`, and `14-test-strategy.md`. Where this plan and a source disagree, the source
> wins. All identifiers use the canonical terms in [`02-glossary.md`](./02-glossary.md); anti-terms are prohibited.

## Summary

Phase 1 delivers a personal-finance application for the India market (INR, UPI, `en-IN`) as **five
independently deployable Spring Boot microservices** — `user-service`, `category-service`,
`expense-service`, `savings-goal-service`, `budget-service` — plus a React 18 + TypeScript (strict) SPA,
governed end-to-end by the Engineering Constitution v1.1.2. Each service owns its own PostgreSQL database
(`identity_db`, `category_db`, `expense_db`, `savings_goal_db`, `budget_db`); cross-context collaboration
flows **only** through Anti-Corruption Ports and asynchronous domain events published via a per-service
**transactional outbox** relayed to Kafka. The technical approach is fixed by the Constitution and spec.md
Section 4 (no alternatives are evaluated). The build order mirrors `12-implementation-plan.md`:
**Phase 0 shared-kernel → Phase 1 user → Phase 2 category+expense → Phase 3 savings+budget → Phase 4 outbox
infrastructure → Phase 5 frontend**, executed task-by-task (TASK-001..111 / T001..T111) under the
3-Commit Loop (RED → GREEN → REFACTOR).

**Scope guard (spec.md §7).** `income-service`, `reporting-service`, and the `notification-service`
*consumer* are **Phase 2 — not built here**. Active services still **write** notification/reporting domain
events to their outbox and the relay publishes them to Kafka per topic retention; **no consumer** processes
them in Phase 1. Any milestone referencing a deferred item is invalid and is omitted from this plan.

## Technical Context

**Language/Version**: Java 21 (LTS) backend; TypeScript 5.x (`strict: true`, `any` prohibited) frontend.

**Primary Dependencies**: Spring Boot 3.x (Web, Security, Data JPA, Validation, Actuator), Spring Kafka,
Flyway, Spring Scheduling; React 18 + Vite, single shared Axios instance, React Query (data-fetching state).
EXIF-stripping image library (e.g. Apache Commons Imaging / metadata-extractor) for receipts.

**Storage**: PostgreSQL — **one database per service** (`identity_db`, `category_db`, `expense_db`,
`savings_goal_db`, `budget_db`), 16 domain tables + per-service `outbox` and `processed_events`; Flyway
migrations with isolated history per DB. MinIO (S3-compatible) Object Storage for receipt images and data
exports — **never** on service disk. Money is `NUMERIC(19,4)` + `currency='INR'`; never `float`/`double`.

**Testing**: JUnit 5 + Mockito (one unit test class per service class — CQ-5); Testcontainers against real
PostgreSQL (one integration test per endpoint group, happy path + 400/401/403/404/409 — CQ-6/CQ-7); Kafka
Testcontainers for the event-flow integration test (CQ-8/§4 Message Broker). Frontend: Vitest + React
Testing Library + MSW (transparent-refresh test; loading/error/empty per data view — FE-4). CI runs build →
`tsc` → lint → unit → integration → OpenAPI contract-diff gate.

**Target Platform**: Linux server containers (Docker Compose: postgres×5, minio, kafka, zookeeper, mailhog).
Responsive web client (desktop/tablet/mobile) over HTTPS.

**Project Type**: Web — polyglot **monorepo** with `shared-kernel/` (zero domain logic), `services/<name>/`
×5, and `frontend/` (per `12-implementation-plan.md` §1.1).

**Performance Goals**: List endpoints paginated (default `size=20`, max `100`); CSV export and Data Export
**streamed**, never fully loaded into memory (CQ-10 / DB-9). CSV import bounded at ≤ 10 MB / ≤ 10 000 rows;
receipt upload ≤ 5 MB, decoded pixels ≤ 25 MP (pixel-flood guard). Stateless services (AL-5) scale
horizontally; access-token validation is signature-only (no DB hit).

**Constraints**: Constitution v1.1.2 is supreme and non-negotiable. JWT HS256, 15-min access token +
7-day rotating refresh token (stored only as SHA-256 hash, `family_id` for reuse detection); BCrypt cost
≥ 12. Ownership is a domain invariant → **403, never 404** (INV-1 / SEC-3). No cross-schema joins or
cross-service FKs (AL-1/DB-2); cross-context refs are bare UUIDs validated via ports (AL-2). Secrets load
only from env/secret store (SEC-6/P5). Mandatory security response headers on every response (S-08 / Doc 07 §1.6).

**Scale/Scope**: Single-tenant per General User (no multi-user/admin). 128 catalogued requirements
(121 Must); **51 REST endpoint groups** across 5 services; 45 catalogued domain events (Phase 1 producers
write all; only the 5 active services consume); 111 atomic tasks across 6 phases.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-evaluated after Phase 1 design (below).*

**Result: PASS — no violations. Complexity Tracking table is empty (no deviations to justify).**

The design is a direct realization of the Constitution; every law has a concrete mechanism in the sources.

### Non-Negotiable Principles (P1–P7)

| Law | How the plan satisfies it | Source |
|-----|---------------------------|--------|
| P1 Contract is Truth | OpenAPI per service is authoritative; CI contract-diff gate; `contracts/` derived from Doc 07. | Doc 07, T003 |
| P2 Type Safety | Java 21; TS `strict`, `any` banned (FE-3 lint/`tsc` gate). | spec §4, FE-3 |
| P3 Layers Sacred | Controller (HTTP/DTO) / Service (logic+ownership) / Repository (data) separation; domain model is pure (Doc 05 §1.1). | CQ-1, Doc 05 |
| P4 Data Ownership | `UserId` on every user-owned aggregate; service-layer ownership check → 403; list queries scoped by `user_id`. | INV-1, Doc 10 §3 |
| P5 Secrets off-repo | `JWT_SECRET`, DB/MinIO/SMTP creds from env only; CI secret-scan. | SEC-6, Doc 10 §6 |
| P6 Untested ≡ Nonexistent | 3-Commit Loop; Mockito unit + Testcontainers integration per task. | Doc 11 §3, Doc 14 |
| P7 Traceable | `traceId` in MDC on every request and log line; carried in event envelope. | CQ-11/12, Doc 08 §1.2 |

### Architecture Laws (AL-1..AL-5)

| Law | Mechanism | Status |
|-----|-----------|--------|
| AL-1 Service isolation | One DB per service; no cross-schema read/join; cross-context refs are bare UUIDs (Doc 09 §8). ArchUnit rule in Phase 4 gate. | PASS |
| AL-2 Communicate via interfaces | 8 Anti-Corruption Ports (Doc 05 §8) + domain events; no foreign repository calls. | PASS |
| AL-3 One context, one service | 5 services = 5 Phase-1 bounded contexts. | PASS |
| AL-4 DTO boundary | `...Request`/`...Response` DTOs only; entities never serialized; events carry Ids + values, not entities. | PASS |
| AL-5 Stateless services | Identity derived from JWT each request; no server session. | PASS |

### API Standards (API-1..API-7)

| Law | Mechanism | Status |
|-----|-----------|--------|
| API-1 Versioned | All paths under `/api/v1/...`. | PASS |
| API-2 Pagination envelope | `PageResponse<T>` (`content,page,size,totalElements,totalPages`) on every list. | PASS |
| API-3 Error envelope | `ErrorResponse` (`timestamp,status,error,message,path,traceId`), no PII in `message`. | PASS |
| API-4 Status codes | 200/201+`Location`/204/400/401/403/404/409/429+`Retry-After`/500 per Doc 07 §1.1. | PASS |
| API-5 DTOs only | Enforced with AL-4; CI serialization check. | PASS |
| API-6 OpenAPI is law | Generated docs; hand-written docs prohibited; contract-diff gate. | PASS |
| API-7 Server-side validation | All input validated server-side regardless of client (Doc 10 §5.1). | PASS |

### Security Standards (SEC-1..SEC-6)

| Law | Mechanism | Status |
|-----|-----------|--------|
| SEC-1 Password hashing | BCrypt cost ≥ 12; plaintext/hash never logged/returned. | PASS |
| SEC-2 Token lifecycle | 15-min access JWT + 7-day rotating refresh (hash-stored), one-time use, family revocation on reuse. | PASS |
| SEC-3 Absolute ownership | 403-never-404 everywhere; opaque UUID ids mitigate enumeration. | PASS |
| SEC-4 Rate limiting | login/register/forgot-password throttled → 429 + `Retry-After`. | PASS |
| SEC-5 Upload validation | JPEG/PNG/WEBP, ≤ 5 MB, magic-byte sniff, EXIF strip, pixel-flood guard, server-generated key. | PASS |
| SEC-6 Secrets externalized | All secrets from env/secret store. | PASS |

### Code Quality & Database (CQ-1..CQ-14)

| Law | Mechanism | Status |
|-----|-----------|--------|
| CQ-1 Strict layering | Controller/Service/Repository separation. | PASS |
| CQ-2 No nulls | Service methods return `Optional<T>`. | PASS |
| CQ-3 No magic literals | Enums/constants; DB enums as `VARCHAR + CHECK`. | PASS |
| CQ-4 Clean main | No TODO/dead code/unused imports (CI lint). | PASS |
| CQ-5/6/7 Tests | Unit per service class; integration per endpoint; happy + failures; independent. | PASS |
| CQ-8 Transactions | Writes `@Transactional`; outbox write in same tx; multi-query reads read-only. | PASS |
| CQ-9 Auto timestamps | `created_at`/`updated_at` on every table via `set_updated_at()` trigger. | PASS |
| CQ-10 Index & stream | Every filter/join column indexed; reports/exports streamed. | PASS |
| CQ-11/12 Logging & MDC | Request log with method/path/status/latency/`traceId`; MDC propagation. | PASS |
| CQ-13 Log hygiene/PII | `PiiMasker`; no email/name/amount/token in logs or error `message`. | PASS |
| CQ-14 Health & metrics | `/actuator/health` + business metrics. | PASS |

### Frontend Standards (FE-1..FE-6)

| Law | Mechanism | Status |
|-----|-----------|--------|
| FE-1 Single Axios client | One shared instance; no `fetch`/second client. | PASS |
| FE-2 Transparent refresh | Interceptor with single in-flight refresh mutex; auto-retry. | PASS |
| FE-3 Strict, no `any` | `tsc` strict gate. | PASS |
| FE-4 Explicit states | Loading/Error/Empty per data view; never render undefined data. | PASS |
| FE-5 Client validation | All forms validate client-side (server-side still authoritative). | PASS |
| FE-6 No hardcoded config | API base URLs from env only. | PASS |

## Project Structure

### Documentation (this feature)

```text
specs/001-daily-expense-tracker/
├── spec.md                       # Manifest / entry point (Section 6 lists the 15 sources)
├── plan.md                       # This file (/speckit-plan output)
├── research.md                   # Phase 0 output — clarifications (none unresolved)
├── data-model.md                 # Phase 1 output — aggregates, VOs, INV-1..10, 16 tables
├── quickstart.md                 # Phase 1 output — end-to-end BDD validation scenarios
├── contracts/                    # Phase 1 output — one contract file per service (5)
│   ├── user-service.md
│   ├── category-service.md
│   ├── expense-service.md
│   ├── savings-goal-service.md
│   └── budget-service.md
├── 01-context-specification.md … 11-agent-instruction-pack.md   # authoritative sources
├── 12-implementation-plan.md     # execution blueprint (this plan aligns to it)
├── 13-task-breakdown.md          # TASK-001..111 atomic breakdown
└── 14-test-strategy.md           # test levels, risk matrix, release gates
```

### Source Code (repository root) — monorepo per `12-implementation-plan.md` §1.1

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

**Structure Decision**: Polyglot monorepo (backend microservices + React SPA). Chosen because the
Constitution mandates one independently deployable service per bounded context (AL-3) with strict isolation
(AL-1), and `shared-kernel/` carries only cross-cutting infra (envelopes, JWT, observability, outbox) with
**zero** domain logic and **zero** repositories so it cannot become a coupling backdoor. Real directories are
those above; no placeholder/option labels remain.

## Phase Plan (aligned to `12-implementation-plan.md`)

Each task is one 3-Commit Loop unit; a phase advances only when its Review Gate is fully green (Doc 12 §3,
Doc 14 release gates). Notification/Reporting/Income are **not** built; affected services write events to the
outbox only.

| Phase | Scope | Key deliverables | Tasks | Exit gate (summary) |
|-------|-------|------------------|-------|---------------------|
| **0** | Shared kernel & infra | Parent POM, Docker Compose, CI; `PageResponse`/`ErrorResponse`/`GlobalExceptionHandler`; `JwtService`+filters; Trace/RequestLogging/PiiMasker; `OutboxEntry`/`EventEnvelope` | T001–T015 | Infra healthy; kernel unit tests green; uniform envelope contract test passes |
| **1** | `user-service` (Identity & Access) | `identity_db` Flyway (5 tables); register/verify/login/refresh(rotation+family revoke)/logout; profile, password change/reset (revoke all), delete, data export; rate limit; `SecureNotificationDeliveryPort` | T016–T039 | Doc 04 §2/§12 BDD pass; BCrypt ≥12; reuse→family revoke; 403-never-404; 429+Retry-After; contract-diff clean |
| **2** | `category-service` + `expense-service` | `category_db`/`expense_db` Flyway; default seed (11, Savings role); `CategoryLookupPort`/`CategoryUsagePort`; Expense CRUD+filters; receipts (magic-byte, EXIF strip, ≤5MB, pixel guard); tags; recurring + scheduler; CSV import/export; Expense events via outbox | T040–T068 | category+expense Testcontainers green; DEFAULT edit/delete→409/403; receipt rejections→400; EXIF stripped; CSV idempotent; money is `NUMERIC(19,4)` |
| **3** | `savings-goal-service` + `budget-service` | `savings_goal_db`/`budget_db` Flyway; goal CRUD + status machine (409 illegal); `ContributionPort`; primary+secondary contribution flows; reconciliation consumer; auto-complete once; budget CRUD; spending consumer; threshold fire once-per-period; rollover ledgers | T069–T093 | suites green; one ContributionEntry per backing Expense; reconcile on edit/delete/unlink; threshold once/period; deactivated fires none; rollover only when enabled |
| **4** | Transactional outbox infra | Per-service `outbox` + `processed_events`; `OutboxWriter` (same tx); `OutboxRelayScheduler`; idempotent consume guard; event-flow IT over real Kafka | T094–T099 | outbox+state atomic (rollback test); consumers idempotent on `eventId`; expense→budget+goal IT green; ArchUnit AL-1 green |
| **5** | Frontend (React 18 + TS strict) | Vite scaffold; `apiConfig` (env); single `axiosClient` + single-flight refresh; auth store + pages; shared Loading/Error/Empty + PaginatedTable + en-IN Money/Date; categories/expenses/goals/budgets features; a11y + responsive; Vitest/RTL/MSW | T100–T111 | `tsc` strict clean (0 `any`); one Axios instance; transparent refresh proven; L/E/E per view; a11y + responsive pass |

**Final release gate (before declaring Phase-1 done).** All 9 hard-stop violation classes (Doc 11 §4) absent
(ArchUnit + lint + static scan); full security suite (Doc 10 §8) green; MinIO buckets private (signed/proxied
only); OpenAPI contract-diff clean for all 5 services; **Income / Reporting / Notification confirmed NOT
implemented**.

## Post-Design Constitution Re-Check

Re-evaluated after producing `data-model.md`, `contracts/`, and `quickstart.md`: **still PASS, no new
violations.** Specific confirmations from the design artifacts:

- **AL-1/DB-2** — `data-model.md` records exactly four cross-context reference columns (`user_id`,
  `category_id`, `savings_goal_id`, `expense_id`), all bare UUIDs validated via ports, zero cross-service FKs.
- **API-2/API-3/API-4** — every `contracts/*` list endpoint returns the pagination envelope; every error path
  uses the uniform error envelope; status-code contract (incl. 201+`Location`, 204, 403-never-404,
  429+`Retry-After`) is encoded per endpoint group.
- **SEC-3** — every `/{id}` and cross-service reference path in `contracts/` documents 403-never-404; lists
  filter by `user_id`.
- **SEC-5** — `expense-service` receipt contract encodes magic-byte sniffing, EXIF strip, ≤5 MB, pixel-flood
  guard, server-generated key, `nosniff`/`Content-Disposition` on download.
- **S-08** — mandatory security response headers documented as a global filter concern, not per-endpoint.
- **Scope** — no `contracts/` file, data-model table, or quickstart scenario references income, reporting,
  dashboard, or notification consumer/Center; deferred budget/goal alert *delivery* appears only as
  outbox-published events with no Phase-1 consumer.

**Residual gate risk: none identified.** The single highest-regression-risk rule (Budget Threshold fires
once per period per threshold — BUD-INV-5) is persisted at the data layer (`fired_*` booleans) and is a
named priority in the Phase 3 gate and Doc 14 security/regression catalogue.

## Complexity Tracking

*No Constitution violations — no entries.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| — | — | — |

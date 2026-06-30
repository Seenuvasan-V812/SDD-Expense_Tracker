# spec.md — Daily Expense Application
## SpecKit Canonical Entry Point

> **This file is the entry point only.**
> It contains SUMMARIES; all authoritative detail lives in the files listed in Section 6.
> When `/speckit-plan` runs, it reads this file first, then follows the manifest below in order.

---

## Section 1 — User Scenarios

| # | Scenario |
|---|----------|
| US-01 | As a General User, I can **record a daily Expense** with an amount, date, Category, Payment Method (UPI / Cash / Credit Card / Debit Card / Net Banking / Other), optional Merchant name, Tags, notes, and an optional Receipt image, so that every purchase is captured accurately in my ledger. |
| US-02 | As a General User, I can **configure a Recurring Expense** with a Recurrence Pattern (Daily, Weekly, Monthly, or Yearly) and an optional end date or maximum Occurrences, so that predictable costs are automatically generated without manual re-entry each period. |
| US-03 | As a General User, I can **create and track Savings Goals** with a Target Amount, optional Target Date, and icon/color, record Contributions from the goal screen or by linking Expenses, and monitor Goal Progress (percentage achieved, Remaining Amount, Projected Completion Date). |
| US-04 | As a General User, I can **define a Budget** per Category or Overall for a Weekly or Monthly Budget Period with optional Rollover, receive a Budget Alert (in-app and email) when spending reaches 80% or exceeds the limit — once per period per threshold — and view remaining budget at a glance. |
| US-05 | As a General User, I can **attach, view, download, and delete a Receipt image** (JPEG/PNG/WEBP, 5 MB max) for any Expense, with the assurance that EXIF metadata is automatically stripped server-side before storage in Object Storage. |
| US-06 | As a General User, I can **bulk-import Expenses from a CSV file** (10 MB max, 10 000 rows max), review a per-row validation report (SUCCEEDED / FAILED / SUCCEEDED_WITH_WARNING), and have valid rows persisted in a single operation — including optional Savings Goal association. |
| US-07 | As a General User, I can **export my Expenses for a given date range** as a downloadable CSV file for offline analysis or record-keeping. |

---

## Section 2 — Functional Requirements Summary

| Bounded Context | Service | Phase | Key Capabilities |
|-----------------|---------|-------|-----------------|
| Identity & Access | `user-service` | 1 | • User registration, Email Verification, login/logout<br>• JWT issuance (15 min Access Token / 7-day rotating Refresh Token stored as hash)<br>• BCrypt password hashing (cost >= 12); rate-limited auth endpoints<br>• Password change/reset with full Refresh Token family revocation<br>• Profile update (name, INR currency, timezone, locale en-IN) and full Data Export |
| Category | `category-service` | 1 | • System-seeded Default Categories (non-deletable, including Savings Category)<br>• User-defined Custom Category CRUD (name, icon, color; typed EXPENSE / INCOME / BOTH)<br>• Deletion blocked while transactions reference the category (reassign first)<br>• CategoryLookupPort validates CategoryId across contexts |
| Expense & Tags | `expense-service` | 1 | • Full Expense CRUD (amount, date, Category, PaymentMethod — all required)<br>• Tag CRUD + multi-tag association; Tag delete detaches without deleting Expenses<br>• Receipt upload/view/download/delete via Object Storage (EXIF stripped, pixel-flood guard, magic-byte validation)<br>• Bulk CSV import (per-row report: SUCCEEDED / FAILED / SUCCEEDED_WITH_WARNING)<br>• CSV export streamed for a date range (no full in-memory load) |
| Recurring Expense | `expense-service` | 1 | • RecurringExpense template with ExpensePrototype and RecurrencePattern (DAILY / WEEKLY / MONTHLY / YEARLY)<br>• RecurrenceBound: end date, max occurrences, or indefinite<br>• Scheduler generates next Occurrence on due date; failure raises RecurringGenerationFailedEvent to outbox<br>• Edit/delete scope: THIS or THIS_AND_FUTURE (truncates template; creates new template for forward changes) |
| Savings Goal | `savings-goal-service` | 1 | • SavingsGoal CRUD with Target Amount, optional Target Date, appearance (icon/color)<br>• Primary Contribution flow (goal screen auto-creates backing Expense under Savings Category) or secondary flow (link existing Expense via Expense API)<br>• Goal lifecycle: ACTIVE, PAUSED, COMPLETED (auto when contributions >= target), ABANDONED<br>• GoalProgress and ProjectedCompletionDate derived; ContributionHistory preserved across lifecycle<br>• SavingsGoalCompletedEvent dispatched via outbox on auto-completion |
| Budget | `budget-service` | 1 | • Budget per Category (CATEGORY scope) or all spending (OVERALL scope), Weekly or Monthly Budget Period<br>• BudgetPeriodLedger tracks spent vs. effectiveLimit (= limit + carriedIn rollover)<br>• FiredThresholdSet ensures each Budget Alert fires at most once per period (EIGHTY_PERCENT / EXCEEDED)<br>• Budget Alerts dispatched via outbox (notification-service consumes in Phase 2)<br>• Activate/deactivate without deletion; Rollover carries unspent into next ledger |
| Income | `income-service` | Phase 2 | • Income entry CRUD with Source field; recurring Income via same Recurrence Pattern<br>• Net cash-flow and Savings Rate inputs for Reporting<br>• IncomeRecordedEvent / IncomeUpdatedEvent / IncomeDeletedEvent defined in event catalog<br>• Deferred — O-04; no API endpoints or income_db schema in Phase 1 |
| Reporting & Dashboard | `reporting-service` | Phase 2 | • Dashboard: monthly totals (income, expenses, Net Savings, Savings Rate), category breakdown, trends, top-5 categories, active Budget Alerts<br>• Reports: Monthly / Yearly / Custom Range with PDF or CSV download<br>• Read-only aggregator consuming domain events; no writes back to source schemas<br>• Deferred — O-01, O-02; no API endpoints in Phase 1 |
| Notification | `notification-service` | Phase 2 | • Consumes: UserRegisteredEvent, PasswordResetRequestedEvent, SavingsGoalCompletedEvent, BudgetThresholdReachedEvent, BudgetExceededEvent, RecurringGenerationFailedEvent, WeeklyDigestDueEvent<br>• Delivers Budget Alerts (in-app + email), goal-completed notification, weekly digest email (opt-in default off), recurring-generation failure alerts<br>• Phase 1: producers write events to outbox only — no notification consumer; events retained per Kafka topic retention<br>• Notification Center UI and REQ-NOTIF-004/005 deferred — O-03 |

---

## Section 3 — Key Entities

Phase-1 **Aggregate Roots** and their owning service (per `05-domain-model.md` §10.1):

| Aggregate Root | Owning Service | Key Local Entities |
|---------------|----------------|--------------------|
| `User` | `user-service` | EmailVerification, RefreshToken, DataExport |
| `Category` | `category-service` | — |
| `Expense` | `expense-service` | Receipt, Tag (Entity with independent lifecycle) |
| `RecurringExpense` | `expense-service` | — |
| `SavingsGoal` | `savings-goal-service` | ContributionEntry |
| `Budget` | `budget-service` | BudgetPeriodLedger |

> **INV-1 — Ownership law:** Every user-owned Aggregate Root carries a `UserId`. All reads/mutations verify ownership — 403, never 404 (P4 / SEC-3). No service may write to another service's schema (AL-1); cross-context references are ID Value Objects only (AL-2).

---

## Section 4 — Technology Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.x, Spring Security |
| Database | PostgreSQL (one database per service: identity_db, category_db, expense_db, savings_goal_db, budget_db), Flyway migrations |
| Message Broker | Apache Kafka + ZooKeeper (Transactional Outbox pattern — event written in same DB transaction as state change) |
| Object Storage | MinIO (S3-compatible) — receipts and data exports; never stored on service disk |
| Frontend | React 18 + TypeScript (strict mode, any prohibited), Vite; single shared Axios instance with transparent JWT refresh; UI/styling/form/charting stack governed by approved registry (`15-ui-design-system.md`, FE-7) |
| Auth | JWT (HS256) — 15 min Access Token / 7-day rotating Refresh Token stored as SHA-256 hash; BCrypt cost >= 12 |
| Testing | JUnit 5, Mockito (unit per service class), Testcontainers (integration against real PostgreSQL per endpoint group) |
| Containers | Docker Compose (postgres x5, minio, kafka, zookeeper, mailhog) |
| Governing Law | Engineering Constitution v1.1.2 — `.specify/memory/constitution.md` |

---

## Section 5 — Success Criteria

| # | Criterion | Source |
|---|-----------|--------|
| SC-01 | All Phase-1 REST endpoints (51 total across 5 services) conform to HTTP semantics in Constitution API-1..API-7: correct status codes (201+Location for creates, 204 for deletes, 403-never-404 for ownership violations), uniform error envelope, and mandatory pagination envelope for all list endpoints. | REQ-API-001..007 |
| SC-02 | EXIF metadata is stripped from 100% of Receipt images before the MinIO write completes — verified by unit test asserting zero EXIF fields in stored bytes; pixel-flood guard (width x height <= 25 MP) and server-side magic-byte type validation (not client-supplied Content-Type) also enforced. | REQ-SEC-005, Doc 10 section 5.2/5.3 |
| SC-03 | Every service method that performs a database lookup returns Optional<T>; zero raw null returns exist in any service-layer class; all monetary values use the Money Value Object (NUMERIC(19,4), INR, never primitive double). | REQ-CQ-003, CQ-2 |
| SC-04 | All cross-service data access goes through the owning service's published Anti-Corruption Port. Zero cross-schema SQL joins exist anywhere in the codebase; all cross-context references are bare UUID columns validated at application layer. | REQ-CQ-007, AL-1/AL-2 |
| SC-05 | Full Phase-1 test coverage: one Mockito unit test class per service class and one Testcontainers integration test per REST endpoint group (covering happy path + 400/401/403/404/409 failure cases), all passing in CI via the 3-Commit Loop (RED to GREEN to REFACTOR). | REQ-TEST-001..004, CQ-5/CQ-6/CQ-7 |

---

## Section 6 — Detailed Specification Manifest

> **Agent instruction — MANDATORY.**
> spec.md is the entry point only. Before authoring plan.md, read ALL files
> below in the listed order. Each file has a defined role; do not skip any.
> Detail in these files is authoritative over any inference from spec.md.
>
> **Path resolution:** all numbered spec files (`01-*` … `14-*`) are
> in the **same directory as this spec.md file**. The constitution path
> (`.specify/memory/constitution.md`) is relative to the **repository root**.

| Order | File | What to extract for plan.md |
|-------|------|-----------------------------|
| 1 | `.specify/memory/constitution.md` | Governing laws v1.2.0 (P1-P7, AL-1-AL-5, API-1-API-7, SEC-1-SEC-6, CQ-1-CQ-14, FE-1-FE-7), bounded contexts, naming rules, phase gates |
| 2 | `01-context-specification.md` | System boundary (C4 Level 1), external integrations (SMTP, MinIO, PostgreSQL), actor definitions (General User, Time-Based Scheduler), India-market requirements (INR, UPI, en-IN locale) |
| 3 | `02-glossary.md` | Canonical service/entity/table/enum names — use exclusively; anti-terms are prohibited in all identifiers and contracts |
| 4 | `03-requirement-catalogue.md` | REQ-* IDs (128 total), MoSCoW priority, Phase 1 vs Phase 2 split; cross-cutting NFRs (REQ-API, REQ-SEC, REQ-CQ, REQ-TEST, REQ-OBS, REQ-DB, REQ-FE) apply to every service |
| 5 | `05-domain-model.md` | Aggregate roots, local entities, Value Objects, Domain Services, Anti-Corruption Ports (8 ports), cross-cutting invariants INV-1..INV-10 |
| 6 | `06-aggregate-specifications.md` | Command set per aggregate (Expense, SavingsGoal, Budget, RecurringExpense), invariants (EXP-INV-*, SG-INV-*, BUD-INV-*, REC-INV-*), domain events per aggregate — map to service milestones |
| 7 | `07-api-specification.md` | 51 endpoint groups across 5 services, DTO shapes, status code contract, pagination envelope, error envelope, mandatory security response headers (S-08) — milestone deliverables |
| 8 | `08-event-catalog.md` | 45 inter-service domain events (standard envelope, at-least-once delivery, Transactional Outbox, idempotency via eventId) — integration milestones; Phase 1 disposition per event |
| 9 | `09-data-contract-specification.md` | Schema per service (16 tables across 5 databases + outbox + processed_events per service), DB rules DB-1..DB-9, cross-schema isolation table — Phase 0 migration milestones |
| 10 | `10-security-specification.md` | JWT chain (HS256, claims, revocation), Token Family reuse detection, 403-never-404 ownership, EXIF and pixel-flood and magic-byte receipt controls, CSV injection prevention, rate limiting, secrets externalisation — security milestones |
| 11 | `04-feature-specifications.md` | BDD scenarios — acceptance criteria per milestone |
| 12 | `11-agent-instruction-pack.md` | Agent protocol constraints: 3-Commit Loop (RED to GREEN to REFACTOR), context loading sequence, prohibited outputs (TODOs, nulls, hardcoded secrets, cross-schema joins) |
| 13 | `12-implementation-plan.md` | Existing Phase 0-5 task breakdown with monorepo scaffold, service task sequences, and phase review gates — plan.md must align with this |
| 14 | `13-task-breakdown.md` | Atomic TASK-001..TASK-111 execution breakdown — each task independently executable, with REQ-* traceability, Depends-On chains, and verifiable Acceptance Criteria |
| 15 | `14-test-strategy.md` | Production-grade test strategy — risk matrix, test levels (unit/integration/contract/E2E/regression), security test catalogue, CUJs, performance targets, automation vs. manual split, and release gate criteria |
| 16 | `15-ui-design-system.md` | Approved UI library registry (FE-7) — full package list with phase scope and constraints; design tokens; component-to-library mapping for all Phase 5 shared and feature components; Tailwind config structure; WCAG AA accessibility standards; Phase 5 compliance checklist |

---

## Section 7 — Phase-Deferral Guard

## Phase 2+ Deferrals — Out of Phase-1 Scope (DO NOT PLAN)

| Deferred | Reason |
|----------|--------|
| `income-service` (REQ-INC-001..004) | O-04 deferral; no API endpoints or income_db schema in Phase 1 |
| `reporting-service` (REQ-DASH-001..006, REQ-RPT-001..005) | O-01, O-02 deferral; no API endpoints in Phase 1 |
| Dashboard charts and analytics | O-01, O-07 deferral; blocked by Reporting context absence |
| Notification Center UI (REQ-NOTIF-004/005) | O-03 deferral; no list/read/delete Notification endpoints in Phase 1 |
| In-app notification delivery | Phase 2 — Phase 1 producers write events to outbox only; no notification-service consumer exists |
| Active Goals panel on Dashboard (REQ-GOAL-014) | O-07 deferral; blocked by Dashboard deferral (O-01) |

> Any plan.md milestone that references the items above is **invalid** and must be removed before execution. Producing services **do** write notification and reporting events to the outbox in Phase 1 (the outbox relay publishes them to Kafka per topic retention policy) — but no consumer processes them until Phase 2.

# Requirement Catalogue — Daily Expense Application

| Field | Value |
|-------|-------|
| **Document** | 03 — Requirement Catalogue |
| **Feature** | Daily Expense Application |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Status** | Draft |
| **Created** | 2026-06-25 |
| **Author Role** | Lead Technical Product Manager |
| **Source Inputs** | `Daily expense tracker Requirements - updated.md`, `.specify/memory/constitution.md` (v1.1.1), `02-glossary.md` |
| **Governing Authority** | [Daily Expense Application — Engineering Constitution](../../.specify/memory/constitution.md) |
| **Vocabulary Authority** | [Ubiquitous Language Glossary](./02-glossary.md) — all requirement statements use Glossary terms verbatim |

> **Role of this document.** This catalogue is the **single source of truth** for requirements.
> Every requirement carries a stable, unique **Trace ID** that downstream artefacts (design,
> tasks, tests, and the future Traceability Matrix) reference. Requirement statements use the
> **exact terms from the [Glossary](./02-glossary.md)** (e.g. *Expense*, *Contribution*,
> *Savings Goal*, *Payment Method*, *UPI*, *Budget Alert*) and avoid prohibited anti-terms.

---

## 1. Conventions

### 1.1 Trace ID scheme

`REQ-<DOMAIN>-<NNN>` — three-digit sequential number within each domain group. IDs are **stable
and never reused**; if a requirement is retired, its ID is retired with it.

| Prefix | Domain Group | Bounded Context (Constitution §2) |
|--------|--------------|-----------------------------------|
| `REQ-USR` | User Management & Account | Identity & Access |
| `REQ-CAT` | Categories | Category |
| `REQ-EXP` | Expense Management | Expense / Transaction |
| `REQ-REC` | Recurring Expenses | Expense / Transaction |
| `REQ-GOAL` | Savings Goals | Savings Goal |
| `REQ-INC` | Income Tracking | Income |
| `REQ-TAG` | Tags | Expense / Transaction |
| `REQ-BUD` | Budgets | Budget |
| `REQ-DASH` | Dashboard | Reporting & Analytics |
| `REQ-RPT` | Reports | Reporting & Analytics |
| `REQ-NOTIF` | Notifications | Notification |
| `REQ-UX` | UX Recommendations | Cross-cutting (frontend) |
| `REQ-RWD` | Responsive Design | Cross-cutting (frontend) |
| `REQ-A11Y` | Accessibility | Cross-cutting (frontend) |
| `REQ-API` | API Design Standards | Cross-cutting (all backend) |
| `REQ-SEC` | Security Standards | Cross-cutting (all backend) |
| `REQ-CQ` | Code Quality Standards | Cross-cutting (all backend) |
| `REQ-TEST` | Testing Standards | Cross-cutting (all services) |
| `REQ-OBS` | Logging & Observability | Cross-cutting (all services) |
| `REQ-DB` | Database Standards | Cross-cutting (all services) |
| `REQ-FE` | Frontend Standards | Cross-cutting (frontend) |

### 1.2 Type values

| Type | Meaning |
|------|---------|
| **Functional** | User-facing behaviour / business capability. |
| **Security** | Authentication, authorization, data protection, input/upload validation. |
| **UX** | Usability, interaction design, responsive layout, accessibility. |
| **NFR** | Non-functional: API design, code quality, testing, observability, persistence. |

### 1.3 Priority values (MoSCoW)

| Priority | Meaning |
|----------|---------|
| **Must** | Mandatory for the first usable release; sourced from requirements stated as "must" or from binding Constitution laws. |
| **Should** | Important but not release-blocking; strongly recommended. |
| **Could** | Desirable enhancement; deferrable without harming the core. |

> **Constitution-derived requirements are non-negotiable.** Any requirement traced to a
> Constitution law (P-, AL-, API-, SEC-, CQ-, FE-) is **Must** by governance, and a violation is
> a release/merge blocker per the Constitution's Governance section — even where the original
> business requirement might otherwise have read as optional.

---

## 2. Functional Requirements

### 2.1 User Management & Account — `REQ-USR`  *(Bounded Context: Identity & Access)*

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-USR-001 | The system must serve the **General User** actor, an India-based individual managing only their own finances. | Functional | Must |
| REQ-USR-002 | The system must operate in **INR** and support Indian locale settings, Indian date formats, and Indian **Payment Methods** including **UPI**. | Functional | Must |
| REQ-USR-003 | A General User must be able to register with full name, email address, and password. | Functional | Must |
| REQ-USR-004 | The system must send an **Email Verification** message upon registration; the account remains **inactive until verified**. | Functional | Must |
| REQ-USR-005 | A General User must be able to log in with email and password. | Functional | Must |
| REQ-USR-006 | A General User must be able to log out, which invalidates the current **Access Token** session. | Functional | Must |
| REQ-USR-007 | A General User must be able to reset their password via a time-limited email link. | Functional | Must |
| REQ-USR-008 | A General User must be able to update their profile — name, preferred currency, and timezone. | Functional | Must |
| REQ-USR-009 | A General User must be able to change their password by providing their current password. | Functional | Must |
| REQ-USR-010 | A General User must be able to delete their own account; deletion must remove all associated data. | Functional | Must |
| REQ-USR-011 | A General User must be able to perform a **Data Export** of all their data as a single downloadable file. | Functional | Must |

### 2.2 Categories — `REQ-CAT`  *(Bounded Context: Category)*

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-CAT-001 | The system must provide **Default Categories** (e.g. Food, Transport, Housing, Health, Entertainment, Shopping, Education, **Savings Category**, Loans, Credit & Debit, Third Party Payments & Other) available to all users and **non-deletable**. | Functional | Must |
| REQ-CAT-002 | A General User must be able to create a **Custom Category** with a name, icon, and color. | Functional | Must |
| REQ-CAT-003 | A General User must be able to edit or delete their own Custom Category. | Functional | Must |
| REQ-CAT-004 | Every Category must be typed as **Expense**, **Income**, or **Both**. | Functional | Must |
| REQ-CAT-005 | Deleting a Category that has associated transactions must be prevented; the General User must reassign those transactions first. | Functional | Must |

### 2.3 Expense Management — `REQ-EXP`  *(Bounded Context: Expense / Transaction)*

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-EXP-001 | A General User must be able to add an **Expense** with: amount (required), date (required), **Category** (required), and **Payment Method** (required). | Functional | Must |
| REQ-EXP-002 | An Expense must support optional fields: description, **Merchant** name, **Receipt** image, **Tags** (multiple), **Savings Goal** link (as a **Contribution**), and notes. | Functional | Must |
| REQ-EXP-003 | A General User must be able to view a paginated, filterable, and sortable list of their Expenses. | Functional | Must |
| REQ-EXP-004 | The Expense list must support filters: date range, Category, Payment Method, Tag, and Savings Goal. | Functional | Must |
| REQ-EXP-005 | The Expense list must support sorting by date and by amount. | Functional | Must |
| REQ-EXP-006 | A General User must be able to edit any field of an existing Expense. | Functional | Must |
| REQ-EXP-007 | When editing an Expense linked to a Savings Goal, the General User must be able to change or remove that association, and the Goal's **Contribution** total must update accordingly. | Functional | Must |
| REQ-EXP-008 | A General User must be able to delete an Expense; if it is linked to a Savings Goal, deletion must reduce the Goal's Contribution total accordingly. | Functional | Must |
| REQ-EXP-009 | A General User must be able to upload a **Receipt** image against an Expense after creation. | Functional | Must |
| REQ-EXP-010 | A General User must be able to view and download the Receipt for an Expense. | Functional | Must |
| REQ-EXP-011 | A General User must be able to delete a Receipt from an Expense. | Functional | Must |
| REQ-EXP-012 | A General User must be able to import Expenses in bulk via a CSV file, with a report of which rows succeeded and which failed (with reasons). | Functional | Must |
| REQ-EXP-013 | The CSV import format must support an optional Savings Goal column: a matching goal name links the Expense to that Savings Goal; a non-matching name imports the row successfully with a warning that the goal association was skipped. | Functional | Must |
| REQ-EXP-014 | A General User must be able to export their Expenses for a given date range as a CSV file. | Functional | Must |

### 2.4 Recurring Expenses — `REQ-REC`  *(Bounded Context: Expense / Transaction)*

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-REC-001 | When adding an Expense, a General User must be able to mark it as a **Recurring Expense** with a **Recurrence Pattern**: Daily, Weekly, Monthly, or Yearly. | Functional | Must |
| REQ-REC-002 | A General User must be able to specify an end date or a maximum number of **Occurrences** for a Recurring Expense; if neither is set, it recurs indefinitely. | Functional | Must |
| REQ-REC-003 | The system must automatically generate the next **Occurrence** of a Recurring Expense on the scheduled date. | Functional | Must |
| REQ-REC-004 | A General User must be able to edit a Recurring Expense with a choice of: edit only this **Occurrence**, or edit this and all future Occurrences. | Functional | Must |
| REQ-REC-005 | A General User must be able to delete a Recurring Expense with a choice of: delete only this **Occurrence**, or delete this and all future Occurrences. | Functional | Must |
| REQ-REC-006 | A General User must be able to view all Recurring Expense templates and their schedules. | Functional | Must |

### 2.5 Savings Goals — `REQ-GOAL`  *(Bounded Context: Savings Goal)*

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-GOAL-001 | A General User must be able to create a **Savings Goal** with a name (required), **Target Amount** (required), optional **Target Date**, optional description, and optional icon and color. | Functional | Must |
| REQ-GOAL-002 | A General User must be able to edit or delete a Savings Goal. | Functional | Must |
| REQ-GOAL-003 | Deleting a Savings Goal must not delete the Expenses linked to it; those Expenses lose their goal association and remain as regular Expenses under the **Savings Category**. | Functional | Must |
| REQ-GOAL-004 | A General User must be able to record a **Contribution** directly from the goal screen by entering an amount and a date; the system must automatically create a backing Expense under the **Savings Category** linked to that Goal, without the user managing the Category or the expense form. | Functional | Must |
| REQ-GOAL-005 | When adding or editing any Expense, a General User must optionally be able to link it to a Savings Goal as a **Contribution**, and the Goal's Contribution total must update immediately upon association. | Functional | Must |
| REQ-GOAL-006 | Contributions from either flow (goal screen or linked Expense) must appear consistently in both the Goal's **Contribution History** and the General User's Expense list. | Functional | Must |
| REQ-GOAL-007 | A Contribution recorded from the goal screen must be visible and editable in the Expense list like any other Expense; editing its amount or date, or deleting it, must update the Goal's Contribution total accordingly. | Functional | Must |
| REQ-GOAL-008 | A General User must be able to view a Savings Goal's detail: Target Amount, Target Date, total contributed, **Remaining Amount**, **percentage achieved** (progress bar), **Projected Completion Date**, and full **Contribution History**. | Functional | Must |
| REQ-GOAL-009 | The **Projected Completion Date** must be derived from the average monthly contribution rate. | Functional | Must |
| REQ-GOAL-010 | A General User must be able to view a list of all Savings Goals showing each goal's progress at a glance, with **Active** and **Completed** goals shown separately. | Functional | Must |
| REQ-GOAL-011 | When total Contributions reach or exceed the Target Amount, the system must automatically set the Goal to **Completed** and notify the General User via an in-app **Notification**. | Functional | Must |
| REQ-GOAL-012 | A General User must be able to manually set a Goal to **Completed** or **Abandoned** regardless of the current Contribution total. | Functional | Must |
| REQ-GOAL-013 | A General User must be able to set a Goal to **Paused**; a Paused goal is excluded from **Projected Completion** calculations and the active goals list, but its **Contribution History** is fully preserved. | Functional | Must |
| REQ-GOAL-014 | The **Dashboard** must include a Goals section showing all Active goals with name, progress bar, contributed amount, Target Amount, and Target Date if set. **[Phase 2 — Deferred (O-07): blocked by Dashboard deferral (O-01); no summary data provider exists until the Reporting & Analytics context is implemented.]** | Functional | Must |

### 2.6 Income Tracking — `REQ-INC`  *(Bounded Context: Income)*

> **⚠️ Phase 2 — Deferred (O-04).** The entire Income bounded context is deferred to Phase 2. No API endpoints, data contract, or implementation scope exist for `income-service` in Phase 1. The `income_db` schema is likewise deferred (Doc 09 Note 6). Domain events in Doc 08 §7.1 remain as placeholders. A dedicated API spec and data contract will be produced in Phase 2.

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-INC-001 | A General User must be able to add an **Income** entry with amount, date, Category, **Source** name, and description. | Functional | Must |
| REQ-INC-002 | A General User must be able to mark Income as recurring, using the same **Recurrence Pattern** options as Expenses. | Functional | Must |
| REQ-INC-003 | A General User must be able to view, edit, and delete Income entries. | Functional | Must |
| REQ-INC-004 | Edit and delete of recurring Income must follow the same "this **Occurrence** / this and future" pattern as Expenses. | Functional | Must |

### 2.7 Tags — `REQ-TAG`  *(Bounded Context: Expense / Transaction)*

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-TAG-001 | A General User must be able to create, rename, and delete personal **Tags**. | Functional | Must |
| REQ-TAG-002 | Tags must be applicable to any Expense for cross-Category grouping (e.g. "vacation", "office"). | Functional | Must |
| REQ-TAG-003 | Deleting a Tag must remove it from all associated Expenses without deleting those Expenses. | Functional | Must |

### 2.8 Budgets — `REQ-BUD`  *(Bounded Context: Budget)*

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-BUD-001 | A General User must be able to set a **Budget** for a specific Category or an **Overall Budget**, for a Weekly or Monthly **Budget Period**. | Functional | Must |
| REQ-BUD-002 | A General User must be able to activate or deactivate a Budget without deleting it. | Functional | Must |
| REQ-BUD-003 | A General User must be able to enable **Rollover** — unspent budget from a Budget Period carries over to the next. | Functional | Must |
| REQ-BUD-004 | The system must raise a **Budget Alert** (in-app and email) when spending reaches the **80% Budget Threshold**. | Functional | Must |
| REQ-BUD-005 | The system must raise a **Budget Alert** (in-app and email) when a Budget is fully exceeded. | Functional | Must |
| REQ-BUD-006 | Each Budget Alert must be sent only **once per Budget Period per Budget Threshold** — no repeated notifications for the same breach. | Functional | Must |
| REQ-BUD-007 | A General User must be able to view each Budget's status: amount set, amount spent, amount remaining, and percentage used. | Functional | Must |

### 2.9 Dashboard — `REQ-DASH`  *(Bounded Context: Reporting & Analytics)*

> **⚠️ Phase 2 — Deferred (O-01).** All Dashboard requirements (REQ-DASH-001…006) are deferred to Phase 2. No API endpoints, domain aggregates, or data contracts exist for the Reporting & Analytics context in Phase 1. Doc 07 §7.4 acknowledges this deferral explicitly. REQ-GOAL-014 (Active goals panel on the Dashboard) is also blocked by this deferral — see §2.5.

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-DASH-001 | The **Dashboard** must display, for the selected month: total income, total expenses, **Net Savings**, **Savings Rate**, number of transactions, and active Budget statuses. | Functional | Must |
| REQ-DASH-002 | The Dashboard must display a spending breakdown by Category for the selected month (chart). | Functional | Must |
| REQ-DASH-003 | The Dashboard must display a spending trend over the last 12 months (chart). | Functional | Must |
| REQ-DASH-004 | The Dashboard must display an income vs. expense comparison over the last 6 months (chart). | Functional | Must |
| REQ-DASH-005 | The Dashboard must display the top 5 spending Categories for the current month. | Functional | Must |
| REQ-DASH-006 | The Dashboard must display any active **Budget Alerts**. | Functional | Must |

### 2.10 Reports — `REQ-RPT`  *(Bounded Context: Reporting & Analytics)*

> **⚠️ Phase 2 — Deferred (O-02).** All Report requirements (REQ-RPT-001…005) are deferred to Phase 2 alongside the Dashboard context (O-01). Monthly, Yearly, and Custom Range report generation and PDF/CSV download have no backing API endpoints or domain aggregates in Phase 1.

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-RPT-001 | A General User must be able to generate a **Monthly Report** for any past month. | Functional | Must |
| REQ-RPT-002 | A General User must be able to generate a **Yearly Report** for any past year. | Functional | Must |
| REQ-RPT-003 | A General User must be able to generate a **Custom Range Report** by selecting a start and end date. | Functional | Must |
| REQ-RPT-004 | Each **Report** must include: total income, total expenses, and **Net Savings** for the period; expense breakdown by Category (amount and percentage); expense breakdown by **Payment Method**; day-wise expense summary; and the list of all transactions in the period. | Functional | Must |
| REQ-RPT-005 | A General User must be able to download any Report as a PDF or CSV file. | Functional | Must |

### 2.11 Notifications — `REQ-NOTIF`  *(Bounded Context: Notification)*

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-NOTIF-001 | The system must send a **Weekly Digest** email every Monday summarising the prior week's total expenses vs. income; this is **opt-in, default off**. | Functional | Should |
| REQ-NOTIF-002 | **Budget Alerts** must be delivered as both an in-app **Notification** and an email. | Functional | Must |
| REQ-NOTIF-003 | Recurring Expense/Income generation failures must surface as an in-app **Notification**. | Functional | Must |
| REQ-NOTIF-004 | A General User must be able to view all unread Notifications in the **Notification Center**. **[Phase 2 — Deferred (O-03): no Notification Center API is defined in Phase 1; no endpoint to list or manage Notifications.]** | Functional | Must |
| REQ-NOTIF-005 | A General User must be able to mark Notifications as read individually or all at once. **[Phase 2 — Deferred (O-03): no Notification Center API is defined in Phase 1.]** | Functional | Must |

---

## 3. UX, Responsive Design & Accessibility Requirements

### 3.1 UX Recommendations — `REQ-UX`  *(Cross-cutting: frontend)*

> Sourced from requirements §1.12 (stated as recommendations, hence **Should/Could**).

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-UX-001 | Multi-step flows should use a step-by-step wizard with progress tracking. | UX | Should |
| REQ-UX-002 | The application should auto-save drafts of in-progress entries. | UX | Should |
| REQ-UX-003 | Forms should pre-fill information from the General User's profile data where applicable. | UX | Should |
| REQ-UX-004 | The application should show eligibility/validation checks in real time. | UX | Should |
| REQ-UX-005 | The application must provide clear validation messages. | UX | Must |
| REQ-UX-006 | The application should display submission confirmation and a reference number for completed actions. | UX | Could |

### 3.2 Responsive Design — `REQ-RWD`  *(Cross-cutting: frontend)*

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-RWD-001 | The application must be usable across desktop, tablet, and mobile. | UX | Must |
| REQ-RWD-002 | The application must adapt layouts for smaller screens. | UX | Must |

### 3.3 Accessibility — `REQ-A11Y`  *(Cross-cutting: frontend)*

| Trace ID | Requirement Statement | Type | Priority |
|----------|-----------------------|------|----------|
| REQ-A11Y-001 | The application must support keyboard navigation. | UX | Must |
| REQ-A11Y-002 | The application must support screen readers. | UX | Must |
| REQ-A11Y-003 | The application must provide accessible color contrast. | UX | Must |
| REQ-A11Y-004 | The application must support text resizing without loss of functionality. | UX | Must |
| REQ-A11Y-005 | Forms must provide accessible error messages and guidance. | UX | Must |
| REQ-A11Y-006 | Images and icons must include alternative text where applicable. | UX | Must |

---

## 4. Non-Functional & Standardization Requirements

> These derive from requirements §2 and are **bound by the Constitution**. The "Constitution Ref"
> column links each to its governing law. All are **Must** by governance.

### 4.1 API Design — `REQ-API`  *(Cross-cutting: all backend)*

| Trace ID | Requirement Statement | Type | Priority | Constitution Ref |
|----------|-----------------------|------|----------|------------------|
| REQ-API-001 | Every endpoint must be versioned from day one under `/api/v1/...`. | NFR | Must | API-1, P1 |
| REQ-API-002 | Every list endpoint must return the pagination envelope: `content`, `page`, `size`, `totalElements`, `totalPages`. | NFR | Must | API-2 |
| REQ-API-003 | HTTP status codes must be used correctly: 200 read/update, 201 create (with `Location` header), 204 delete, 400 validation, 401 unauthenticated, 403 unauthorized, 404 not found, 409 conflict, 500 server error. | NFR | Must | API-4 |
| REQ-API-004 | Every error response must use the uniform envelope: `timestamp`, `status`, `error`, `message`, `path`, `traceId`. | NFR | Must | API-3 |
| REQ-API-005 | API documentation must be auto-generated from the **OpenAPI** contract and always current; hand-written API docs are prohibited. | NFR | Must | API-6, P1 |
| REQ-API-006 | DTOs must be used for all API input and output; JPA entities must never be serialized to a response. | NFR | Must | API-5, AL-4 |
| REQ-API-007 | All user inputs must be validated server-side regardless of client-side validation. | Security | Must | API-7 |

### 4.2 Security — `REQ-SEC`  *(Cross-cutting: all backend)*

| Trace ID | Requirement Statement | Type | Priority | Constitution Ref |
|----------|-----------------------|------|----------|------------------|
| REQ-SEC-001 | Passwords must be hashed with BCrypt (minimum cost factor 12); plain-text passwords must never appear in logs, responses, or the database. | Security | Must | SEC-1 |
| REQ-SEC-002 | Authentication must use short-lived **Access Tokens** (JWT, 15-minute expiry) with rotating **Refresh Tokens** (7-day expiry); on each refresh the old token is invalidated. | Security | Must | SEC-2 |
| REQ-SEC-003 | Every endpoint accessing user-owned data must verify the resource belongs to the requesting General User; foreign access must return **403, never 404**. | Security | Must | P4, SEC-3 |
| REQ-SEC-004 | Auth endpoints (login, register, forgot-password) must be rate-limited to prevent brute force. | Security | Must | SEC-4 |
| REQ-SEC-005 | **Receipt** uploads must be validated for type and size: only JPEG, PNG, WEBP accepted, maximum 5 MB. | Security | Must | SEC-5 |
| REQ-SEC-006 | Sensitive configuration (DB password, JWT secret, object-store/MinIO credentials, SMTP password) must load only from environment variables or an excluded secrets file — never hardcoded or committed. | Security | Must | SEC-6, P5 |

### 4.3 Code Quality — `REQ-CQ`  *(Cross-cutting: all backend)*

| Trace ID | Requirement Statement | Type | Priority | Constitution Ref |
|----------|-----------------------|------|----------|------------------|
| REQ-CQ-001 | Business logic must live in the service layer; controllers handle only request parsing and response shaping; repositories handle only data access. | NFR | Must | CQ-1, P3 |
| REQ-CQ-002 | A service method must not call another service's repository directly; cross-context data must go through the owning service. | NFR | Must | AL-2, CQ-1 |
| REQ-CQ-003 | Service methods must never return `null`; lookups that may find nothing return `Optional<T>`. | NFR | Must | CQ-2 |
| REQ-CQ-004 | All database write operations must run in a transaction; multi-query reads use a read-only transaction. | NFR | Must | CQ-8 |
| REQ-CQ-005 | No hardcoded string literals for constants (status values, enum strings, error messages); use enums or constants. | NFR | Must | CQ-3 |
| REQ-CQ-006 | Unused imports, commented-out code, and TODO comments must not be merged to the `main` branch. | NFR | Must | CQ-4 |
| REQ-CQ-007 | Each bounded context must be an independently deployable microservice; a service must not access another service's database, schema, or tables. | NFR | Must | AL-1, AL-3 |
| REQ-CQ-008 | Services must be stateless; identity is derived from the JWT on every request. | NFR | Must | AL-5 |

### 4.4 Testing — `REQ-TEST`  *(Cross-cutting: all services)*

| Trace ID | Requirement Statement | Type | Priority | Constitution Ref |
|----------|-----------------------|------|----------|------------------|
| REQ-TEST-001 | Every service class must have a corresponding unit test class verifying business rules in isolation using mocks. | NFR | Must | CQ-5 |
| REQ-TEST-002 | Every API endpoint must have an integration test that starts the real application against a real PostgreSQL via Testcontainers. | NFR | Must | CQ-6 |
| REQ-TEST-003 | Tests must cover both the happy path and key failure cases (invalid input, unauthorized access, not found). | NFR | Must | CQ-7 |
| REQ-TEST-004 | Tests must be independent — no test may rely on state left by another test. | NFR | Must | CQ-7 |

### 4.5 Logging & Observability — `REQ-OBS`  *(Cross-cutting: all services)*

| Trace ID | Requirement Statement | Type | Priority | Constitution Ref |
|----------|-----------------------|------|----------|------------------|
| REQ-OBS-001 | Every incoming HTTP request must be logged with method, path, response status, response time, and a unique `traceId`. | NFR | Must | CQ-11 |
| REQ-OBS-002 | The `traceId` must be present on every log line within a request's lifecycle (via MDC). | NFR | Must | CQ-12, P7 |
| REQ-OBS-003 | Log levels must be used correctly: DEBUG (internal flow), INFO (business events), WARN (recoverable problems), ERROR (unexpected failures, with stack trace). | NFR | Must | CQ-13 |
| REQ-OBS-004 | Personally identifiable information (email, name, amounts) must never appear in log messages. | Security | Must | CQ-13 |
| REQ-OBS-005 | Application health must be exposed at `/actuator/health` for load-balancer probing. | NFR | Must | CQ-14 |
| REQ-OBS-006 | Key business metrics (expenses created, active users, Budget Alerts sent, report generation time) must be exposed for monitoring. | NFR | Must | CQ-14 |

### 4.6 Database — `REQ-DB`  *(Cross-cutting: all services)*

| Trace ID | Requirement Statement | Type | Priority | Constitution Ref |
|----------|-----------------------|------|----------|------------------|
| REQ-DB-001 | Every table must have `created_at` and `updated_at` timestamps, populated automatically. | NFR | Must | CQ-9 |
| REQ-DB-002 | Any column used to filter or join must have an index. | NFR | Must | CQ-10 |
| REQ-DB-003 | Bulk queries (reports, exports) must not load all records into memory; they must use pagination or streaming. | NFR | Must | CQ-10 |

### 4.7 Frontend Standards — `REQ-FE`  *(Cross-cutting: frontend)*

| Trace ID | Requirement Statement | Type | Priority | Constitution Ref |
|----------|-----------------------|------|----------|------------------|
| REQ-FE-001 | All API calls must go through a single shared Axios client instance; no component may call `fetch` or create its own Axios instance. | NFR | Must | FE-1 |
| REQ-FE-002 | The shared Axios client must handle **Access Token** refresh transparently, retrying the original request without the component's involvement. | NFR | Must | FE-2 |
| REQ-FE-003 | All forms must validate input on the client side before submitting (server-side validation per REQ-API-007 still applies). | UX | Must | FE-5 |
| REQ-FE-004 | Loading, error, and empty states must be handled explicitly for every data-fetching component; no component may render with undefined data. | UX | Must | FE-4 |
| REQ-FE-005 | TypeScript strict mode must be enabled; the `any` type is prohibited. | NFR | Must | FE-3, P2 |
| REQ-FE-006 | No hardcoded API base URLs in components; all configuration comes from environment variables. | NFR | Must | FE-6 |

---

## 5. Catalogue Summary

| Domain Group | Trace ID Range | Count | Predominant Type |
|--------------|----------------|-------|------------------|
| User Management & Account | REQ-USR-001…011 | 11 | Functional |
| Categories | REQ-CAT-001…005 | 5 | Functional |
| Expense Management | REQ-EXP-001…014 | 14 | Functional |
| Recurring Expenses | REQ-REC-001…006 | 6 | Functional |
| Savings Goals | REQ-GOAL-001…014 | 14 | Functional |
| Income Tracking | REQ-INC-001…004 | 4 | Functional |
| Tags | REQ-TAG-001…003 | 3 | Functional |
| Budgets | REQ-BUD-001…007 | 7 | Functional |
| Dashboard | REQ-DASH-001…006 | 6 | Functional |
| Reports | REQ-RPT-001…005 | 5 | Functional |
| Notifications | REQ-NOTIF-001…005 | 5 | Functional |
| UX Recommendations | REQ-UX-001…006 | 6 | UX |
| Responsive Design | REQ-RWD-001…002 | 2 | UX |
| Accessibility | REQ-A11Y-001…006 | 6 | UX |
| API Design | REQ-API-001…007 | 7 | NFR/Security |
| Security | REQ-SEC-001…006 | 6 | Security |
| Code Quality | REQ-CQ-001…008 | 8 | NFR |
| Testing | REQ-TEST-001…004 | 4 | NFR |
| Logging & Observability | REQ-OBS-001…006 | 6 | NFR/Security |
| Database | REQ-DB-001…003 | 3 | NFR |
| Frontend Standards | REQ-FE-001…006 | 6 | NFR/UX |
| **Total** | — | **128** | — |

### 5.1 Priority distribution

| Priority | Count | Notes |
|----------|-------|-------|
| **Must** | 121 | All functional requirements stated as "must", and all Constitution-bound NFR/Security/UX laws. |
| **Should** | 5 | Weekly Digest (opt-in) and most UX recommendations (§1.12). |
| **Could** | 2 | Submission confirmation/reference-number UX enhancement; deferrable polish. |

### 5.2 Type distribution

| Type | Count |
|------|-------|
| Functional | 80 |
| Security | 9 |
| UX | 18 |
| NFR | 21 |

---

## 6. Notes & Assumptions

1. **MoSCoW assignment.** The source requirements predominantly use "must", so almost all
   functional items are **Must**. Only items explicitly framed as recommendations (§1.12 UX) or
   as opt-in/default-off (Weekly Digest, REQ-NOTIF-001) are downgraded to **Should/Could**.
   Constitution-bound items are **Must** by governance regardless of phrasing.
2. **Glossary fidelity.** Requirement statements use Glossary terms verbatim and avoid the
   prohibited anti-terms (e.g. *Expense* not "Outflow", *Income* not "Inflow", *Contribution*
   not "Deposit", *Source* for Income vs. *Merchant* for Expense). See [02-glossary.md](./02-glossary.md).
3. **Contribution duality.** REQ-GOAL-004 (primary flow) and REQ-GOAL-005 (secondary flow) are
   intentionally distinct requirements because each is independently testable, but both resolve to
   the same invariant: a Contribution is always backed by an Expense under the Savings Category.
4. **Cross-cutting NFRs apply to every service.** REQ-API-*, REQ-SEC-*, REQ-CQ-*, REQ-TEST-*,
   REQ-OBS-*, REQ-DB-* are not owned by one bounded context; they constrain all backend services.
   The future Traceability Matrix should map each functional requirement to the cross-cutting NFRs
   it must satisfy (e.g. every list requirement → REQ-API-002 pagination; every user-data endpoint
   → REQ-SEC-003 ownership).
5. **No new scope introduced.** This catalogue extracts only what the requirements and constitution
   state. No multi-user, admin, bank-feed, or payment-execution requirements were added (consistent
   with the §1.3 scope boundary in `01-context-specification.md`).
6. **Traceability forward.** Each Trace ID is the anchor downstream artefacts cite. The planned
   Traceability Matrix will map `REQ-*` → design element → task → test, completing
   requirement → verification coverage.
7. **Phase 2 deferral.** The Reporting & Analytics context (Dashboard, Reports), the Notification
   Center read/manage endpoints (REQ-NOTIF-004/005), and the Income context (REQ-INC-001…004) have
   no Phase 1 API or data contract. Their requirements remain in this catalogue with a
   **[Phase 2 — Deferred (O-xx)]** inline marker and are excluded from the Phase 1 implementation
   plan, task breakdown, and Traceability Matrix. Phase 2 scoping will produce dedicated API and
   data-contract specifications for each context. The deferral is acknowledged in Doc 07 §7.4
   (API scope) and Doc 09 Note 6 (`income_db` schema).

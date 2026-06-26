# Domain Model Specification — Daily Expense Application

| Field | Value |
|-------|-------|
| **Document** | 05 — Domain Model Specification |
| **Feature** | Daily Expense Application |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Status** | Draft |
| **Created** | 2026-06-25 |
| **Author Role** | Domain-Driven Design (DDD) Architect |
| **Source Inputs** | `.specify/memory/constitution.md` (v1.1.1), `02-glossary.md`, `03-requirement-catalogue.md` |
| **Governing Authority** | [Daily Expense Application — Engineering Constitution](../../.specify/memory/constitution.md) |
| **Vocabulary Authority** | [Ubiquitous Language Glossary](./02-glossary.md) |
| **Traceability Authority** | [Requirement Catalogue](./03-requirement-catalogue.md) |

> **Purpose.** Define the tactical DDD model for the five core bounded contexts —
> **Identity & Access**, **Category**, **Expense / Transaction**, **Savings Goal**, and **Budget** —
> in terms of **Aggregate Roots**, **local Entities**, **Value Objects**, and **Domain Services**.
> All names follow the [Glossary](./02-glossary.md) and Constitution §6 naming conventions; all
> structure obeys the **Layers are Sacred** (P3 / CQ-1) and **Service Isolation** (AL-1/AL-2/AL-3)
> laws.

---

## 1. Modelling Rules (binding for this document)

### 1.1 "Layers are Sacred" (Constitution P3, CQ-1)

This document models the **domain layer only** — Aggregates, Entities, Value Objects, and Domain
Services. It deliberately says nothing about controllers, DTOs, repositories, or persistence
mapping. Per CQ-1:

- **Controllers** handle HTTP + DTOs only — *out of scope here*.
- **Services** (application + domain) hold all business logic — *the subject of this document*.
- **Repositories** handle data access only — *referenced abstractly as "the aggregate is loaded /
  persisted", never modelled with query detail*.

> A **Domain Service** in this document is a stateless operation expressing business logic that does
> not naturally belong to a single Entity or Value Object. It lives in the service layer (CQ-1) and
> never returns `null` (CQ-2) — lookups that may miss return `Optional<T>`.

### 1.2 "Service Isolation" (Constitution AL-1, AL-2, AL-3)

- **AL-3 — One context, one service.** Each bounded context below maps to exactly one independently
  deployable microservice owning its own PostgreSQL schema/database.
- **AL-1 — No cross-schema access.** An aggregate in one context **never** holds an object reference
  to an aggregate in another context. Cross-context links are modelled as **ID Value Objects**
  (e.g. `UserId`, `CategoryId`, `SavingsGoalId`) — never as embedded foreign aggregates.
- **AL-2 — Communicate via interfaces.** When one context needs another's data (e.g. Expense needs
  to validate a `CategoryId`), it calls the owning service's published interface — modelled here as
  an **Anti-Corruption Port** (an outbound interface the domain depends on), not a repository.

### 1.3 Reference-by-identity rule

> **Within an aggregate:** object references and cascade (the root controls its locals).
> **Across aggregates (same or different context):** reference by **ID Value Object** only.

This keeps aggregates small, transactional boundaries clean (CQ-8), and contexts isolated (AL-1).

### 1.4 Notation

```text
Aggregate Root      «AR»        Entity          «E»
Value Object        «VO»        Domain Service  «DS»
Anti-Corruption Port«ACP»       Enum            «enum»
Relationship        1 ──── 1    (one-to-one)
                    1 ───< *    (one-to-many)
Cross-context ref   ⇢ by Id     (ID Value Object, no object reference — AL-1)
```

### 1.5 Shared kernel Value Objects

A minimal **Shared Kernel** of Value Objects is defined once and reused (by copy, not shared DB) in
every context. They are immutable, self-validating, and carry no identity.

| «VO» | Definition | Invariants |
|------|------------|------------|
| **Money** | An amount in **INR** (the operating currency). | Non-null scale-2 decimal; currency is INR; sign rules are context-specific (e.g. Expense amount > 0). Never a primitive `double`. |
| **AuditTimestamps** | `createdAt`, `updatedAt`. | Auto-populated; every persisted aggregate carries them (CQ-9). |
| **UserId** | Identity of the owning General User. | Non-null on every user-owned aggregate; basis of ownership checks (SEC-3 / P4). |
| **DateOnly** | A calendar date in Indian locale/format. | Valid date; used for transaction date, target date, period bounds. |

> **Ownership is a domain invariant, not just a guard.** Every user-owned Aggregate Root carries a
> `UserId`. The ownership check that returns **403, never 404** (REQ-SEC-003) is enforced in the
> application/domain service before any state change.

---

## 2. Context Map (relationships between contexts)

```text
                         ┌───────────────────────┐
                         │  Identity & Access     │  «owns» UserId
                         │  (user-service)        │
                         └───────────┬───────────┘
                                     │ UserId ⇢ (every user-owned aggregate)
        ┌────────────────────────────┼─────────────────────────────┐
        │                            │                             │
        ▼                            ▼                             ▼
┌───────────────┐          ┌──────────────────┐          ┌──────────────────┐
│  Category      │  Cat-   │ Expense /         │  Goal-   │  Savings Goal     │
│ (category-svc) │◀--Id----│ Transaction       │---Id--- ▶│ (savings-goal-svc)│
└───────────────┘  ⇢ by   │ (expense-service) │  ⇢ by    └──────────────────┘
     ▲   validates Id      └─────────┬────────┘   Id            ▲
     │                               │  spending feeds          │ contribution = Expense
     │ CategoryId ⇢                  ▼                          │ under Savings Category
     │                     ┌──────────────────┐                 │
     └─────────────────────│  Budget           │─────────────────┘
        validates Cat       │ (budget-service) │
                            └──────────────────┘

Legend:  ⇢ by Id  = cross-context reference held as an ID Value Object (AL-1);
         resolution/validation happens via the owning service's Anti-Corruption Port (AL-2).
```

**Context relationships (DDD patterns):**

| Upstream (U) → Downstream (D) | Pattern | Why |
|-------------------------------|---------|-----|
| Identity & Access → all | **Conformist / Shared Id** | Every context conforms to `UserId` as the ownership key. |
| Category → Expense | **Customer–Supplier** (ACP) | Expense must validate that a `CategoryId` exists, is owned/visible, and is typed to allow Expense. |
| Category → Budget | **Customer–Supplier** (ACP) | A per-Category Budget references a `CategoryId`. |
| Savings Goal ↔ Expense | **Partnership** | A Contribution is *realised* as an Expense under the Savings Category; the goal tracks the link and totals. Tight collaboration, two aggregates. |
| Expense → Budget | **Published event / query** | Budget evaluates spending derived from Expenses for a Category/period. |

> **Contribution invariant across contexts (REQ-GOAL-004/005/007).** A **Contribution** is *always*
> backed by an **Expense** (owned by the Expense context) categorised under the **Savings Category**
> and tagged with a `SavingsGoalId`. The Savings Goal context owns the *link and the running total*;
> the Expense context owns the *money record*. There is exactly one source of truth for the amount:
> the backing Expense.

---

## 3. Bounded Context — Identity & Access  *(service: `user-service`)*

Owns: the General User account, credentials, verification, profile, sessions/tokens, account
deletion, and data export. (REQ-USR-001…011, REQ-SEC-001…004, REQ-SEC-006.)

### 3.1 Aggregate Roots, Entities, Value Objects

```text
«AR» User                                   (the General User account; root)
 ├─ identity:        «VO» UserId            (1──1)
 ├─ fullName:        «VO» PersonName
 ├─ email:           «VO» EmailAddress      (unique; the login identifier)
 ├─ passwordHash:    «VO» PasswordHash      (BCrypt, cost ≥ 12 — SEC-1)
 ├─ status:          «enum» AccountStatus   { INACTIVE_UNVERIFIED, ACTIVE, DELETED }
 ├─ profile:         «VO» UserProfile       (1──1)   { preferredCurrency=INR default, timezone, locale }
 ├─ verification:    «E»  EmailVerification (1──1; local entity, lifecycle-owned by User)
 ├─ refreshTokens:   «E»  RefreshToken      (1───< *; rotating — SEC-2)
 └─ audit:           «VO» AuditTimestamps
```

| Element | Kind | Notes / Invariants |
|---------|------|--------------------|
| **User** | «AR» | Root of the identity aggregate. Cannot authenticate while `status = INACTIVE_UNVERIFIED` (REQ-USR-004). Deletion sets `DELETED` and triggers data removal (REQ-USR-010). |
| **EmailVerification** | «E» | Local entity holding the time-limited verification token; consumed once to move `User` to `ACTIVE`. |
| **RefreshToken** | «E» | Local entity; 7-day expiry, **rotated** on each refresh — the prior token is invalidated immediately (SEC-2). One User has many over time. |
| **UserId** | «VO» | Stable identity; exported to every other context as the ownership key. |
| **EmailAddress** | «VO» | Validated format; unique across the context; immutable once set (re-validation required to change). |
| **PasswordHash** | «VO» | BCrypt hash (cost ≥ 12). Plain text never stored, logged, or returned (SEC-1, CQ-13). |
| **PersonName** | «VO» | Non-empty full name. |
| **UserProfile** | «VO» | `preferredCurrency` (defaults INR), `timezone`, `locale` (Indian locale supported — REQ-USR-002, REQ-USR-008). |
| **AccountStatus** | «enum» | Lifecycle states above. |

### 3.2 Domain Services

| «DS» | Operation (spans multiple entities / enforces cross-entity rules) | Requirements |
|------|------------------------------------------------------------------|--------------|
| **RegistrationService** | Create `User` as `INACTIVE_UNVERIFIED`, generate `EmailVerification`, reject duplicate `EmailAddress` (409). | REQ-USR-003/004, REQ-SEC-001 |
| **AuthenticationService** | Verify credentials against `PasswordHash`; refuse unverified accounts; issue Access Token + initial Refresh Token. | REQ-USR-005, REQ-SEC-002 |
| **TokenRotationService** | Validate a Refresh Token, mint a new Access/Refresh pair, invalidate the old Refresh Token. | REQ-SEC-002 |
| **AccountLifecycleService** | Password change/reset, profile update, account deletion (cascade removal), and **DataExport** assembly. | REQ-USR-007…011 |

> **Note on Data Export (REQ-USR-011).** Identity orchestrates the export *request*, but the actual
> per-context data is gathered via each owning service's published interface (AL-2) — Identity
> never reads another context's schema (AL-1).

---

## 4. Bounded Context — Category  *(service: `category-service`)*

Owns: Default (non-deletable) and Custom categories, and category typing. (REQ-CAT-001…005.)

### 4.1 Aggregate Roots, Entities, Value Objects

```text
«AR» Category
 ├─ identity:     «VO» CategoryId          (1──1)
 ├─ owner:        «VO» UserId (nullable)    ⇢ null for Default (system) categories; set for Custom
 ├─ name:         «VO» CategoryName
 ├─ origin:       «enum» CategoryOrigin     { DEFAULT, CUSTOM }
 ├─ type:         «enum» CategoryType       { EXPENSE, INCOME, BOTH }
 ├─ appearance:   «VO» Appearance           { icon, color }   (1──1)
 ├─ systemRole:   «enum» SystemCategoryRole { NONE, SAVINGS }  (SAVINGS = the Savings Category)
 └─ audit:        «VO» AuditTimestamps
```

| Element | Kind | Notes / Invariants |
|---------|------|--------------------|
| **Category** | «AR» | A `DEFAULT` Category is non-deletable and visible to all users (REQ-CAT-001). The **Savings Category** is a `DEFAULT` Category with `systemRole = SAVINGS`. A `CUSTOM` Category has a non-null `owner` and is editable/deletable only by that owner (REQ-CAT-002/003). |
| **CategoryId** | «VO» | Exported to Expense and Budget contexts as a reference Id (AL-1). |
| **CategoryName** | «VO» | Non-empty; unique per owner for custom categories. |
| **CategoryType** | «enum» | `EXPENSE` / `INCOME` / `BOTH` (REQ-CAT-004). Constrains which transaction kinds may use it. |
| **CategoryOrigin** | «enum» | `DEFAULT` vs `CUSTOM`. |
| **SystemCategoryRole** | «enum» | Marks the Savings Category used to back Contributions. |
| **Appearance** | «VO» | `icon` + `color`. |

### 4.2 Domain Services

| «DS» | Operation | Requirements |
|------|-----------|--------------|
| **CategoryAuthoringService** | Create/edit/delete a Custom Category; reject deletion or edit of Default categories; enforce name uniqueness per owner. | REQ-CAT-001/002/003 |
| **CategoryDeletionGuard** | Refuse to delete a Category that still has associated transactions; instruct reassignment first. Because transactions live in the Expense/Income contexts, this guard queries those contexts via their **ACP** (AL-2) before allowing deletion. | REQ-CAT-005 |

> **Why deletion is a Domain Service, not an Entity method.** The rule spans *another context's*
> data (transactions). Per CQ-1/AL-2 the Category aggregate cannot read the Expense schema; the
> service coordinates the check through a published port.

---

## 5. Bounded Context — Expense / Transaction  *(service: `expense-service`)*

Owns: Expenses, Receipts, Tags-on-Expense, recurring Expense generation, CSV import/export.
(REQ-EXP-001…014, REQ-REC-001…006, REQ-TAG-001…003, REQ-SEC-005.)

### 5.1 Aggregate: Expense

```text
«AR» Expense
 ├─ identity:        «VO» ExpenseId             (1──1)
 ├─ owner:           «VO» UserId                 ⇢ ownership key (SEC-3)
 ├─ amount:          «VO» Money                  (> 0, INR)
 ├─ date:            «VO» DateOnly
 ├─ categoryRef:     «VO» CategoryId             ⇢ by Id (validated via Category ACP — AL-2)
 ├─ paymentMethod:   «enum» PaymentMethod        { UPI, CASH, CREDIT_CARD, DEBIT_CARD, NET_BANKING, OTHER }
 ├─ description:     «VO» Description?            (optional)
 ├─ merchant:        «VO» MerchantName?           (optional)
 ├─ notes:           «VO» Notes?                  (optional)
 ├─ tags:            Set<«VO» TagId>               (1───< *  — cross-aggregate Tag refs by Id; Tag is a «E» with its own lifecycle — see §5.4)
 ├─ receipt:         «E»  Receipt?                (1──1 optional; local entity)
 ├─ goalLink:        «VO» SavingsGoalId?          ⇢ by Id (optional; presence ⇒ this Expense is a Contribution)
 ├─ recurrenceRef:   «VO» RecurringExpenseId?     ⇢ Id of the template that generated this Occurrence (optional)
 └─ audit:           «VO» AuditTimestamps
```

| Element | Kind | Notes / Invariants |
|---------|------|--------------------|
| **Expense** | «AR» | Core transaction (money out). `amount` required > 0; `date`, `categoryRef`, `paymentMethod` required (REQ-EXP-001). Editing/deleting an Expense whose `goalLink` is set must notify the Savings Goal context so the Contribution total updates (REQ-EXP-007/008) — via published event/ACP, never by writing the goal schema (AL-1). |
| **Receipt** | «E» | Local entity owned by the Expense: stored image reference (object storage), `mimeType ∈ {JPEG,PNG,WEBP}`, `size ≤ 5 MB` (SEC-5). Lifecycle is fully controlled by its Expense (add after creation, view/download, delete — REQ-EXP-009/010/011). |
| **PaymentMethod** | «enum» | India-aware set including **UPI** (REQ-USR-002, REQ-EXP-001). Required. |
| **Tag** | «E» | A user-owned label with stable identity (`TagId`). Tags are a distinct Entity managed by `TagManagementService` (§5.4) with their own create/rename/delete lifecycle independent of any Expense. An Expense holds cross-aggregate references to Tags via `TagId` (AL-1). Deleting a Tag detaches it from all Expenses without deleting those Expenses (REQ-TAG-003). |
| **TagId** | «VO» | The stable identity of a `Tag`; the cross-aggregate reference held in `Expense.tags` (AL-1). |
| **MerchantName / Description / Notes** | «VO» | Optional free-text VOs. *Merchant* is the Expense counterpart of Income's *Source* (never unified — Glossary). |
| **CategoryId / SavingsGoalId / RecurringExpenseId** | «VO» | Cross-aggregate / cross-context references **by Id only** (AL-1). |

### 5.2 Aggregate: RecurringExpense (template)

```text
«AR» RecurringExpense                     (the template; distinct from the Occurrences it generates)
 ├─ identity:        «VO» RecurringExpenseId   (1──1)
 ├─ owner:           «VO» UserId
 ├─ prototype:       «VO» ExpensePrototype      { amount, categoryRef, paymentMethod, description, ... }
 ├─ pattern:         «VO» RecurrencePattern     { frequency ∈ {DAILY,WEEKLY,MONTHLY,YEARLY}, anchorDate }
 ├─ bound:           «VO» RecurrenceBound        { endDate? | maxOccurrences? | indefinite }
 ├─ generatedCount:  int                          (occurrences produced so far)
 └─ audit:           «VO» AuditTimestamps
```

| Element | Kind | Notes / Invariants |
|---------|------|--------------------|
| **RecurringExpense** | «AR» | Separate aggregate from `Expense`. Generates the next **Occurrence** (a new `Expense` with `recurrenceRef` set) on schedule (REQ-REC-001/002/003). Edit/delete "this occurrence vs. this and future" is resolved against this template (REQ-REC-004/005). |
| **RecurrencePattern** | «VO» | Frequency + anchor. |
| **RecurrenceBound** | «VO» | End date, max occurrences, or indefinite (REQ-REC-002). |
| **ExpensePrototype** | «VO» | The template values copied into each generated Occurrence. |

### 5.3 Relationships (intra-context)

```text
User (Identity) 1 ───< * Expense            ⇢ by UserId (AL-1)
Category        1 ───< * Expense            ⇢ by CategoryId (validated via ACP)
Expense         1 ──── 0..1 Receipt         (local entity, cascade)
Expense         1 ───< * Tag                ⇢ by TagId (Tag is a «E» with its own lifecycle; cross-aggregate ref, AL-1)
RecurringExpense 1 ───< * Expense           ⇢ by RecurringExpenseId (each generated Occurrence)
SavingsGoal     1 ───< * Expense            ⇢ by SavingsGoalId (Contribution backing; partnership)
```

### 5.4 Domain Services

| «DS» | Operation | Requirements |
|------|-----------|--------------|
| **ExpenseService** | Create/edit/delete an Expense; enforce required fields, positive `Money`, ownership (403-never-404). On goal-link change, emit a Contribution-changed signal to Savings Goal (ACP). | REQ-EXP-001/002/006/007/008, REQ-SEC-003 |
| **ReceiptService** | Validate (type, ≤ 5 MB), store/retrieve/delete the Receipt via object storage; enforce ownership of the parent Expense. | REQ-EXP-009/010/011, REQ-SEC-005 |
| **TagManagementService** | Create/rename/delete canonical Tags; on tag delete, detach the Tag from all Expenses without deleting them. | REQ-TAG-001/002/003 |
| **RecurringExpenseGenerator** | Generate the next Occurrence per `RecurrencePattern`/`RecurrenceBound`; on failure, raise a generation-failure Notification (via Notification ACP). | REQ-REC-003, REQ-NOTIF-003 |
| **ExpenseImportService** | Bulk CSV import with per-row success/failure reporting; optional Savings Goal column — link if goal name matches (via Savings Goal ACP), else import with a skipped-association warning. | REQ-EXP-012/013 |
| **ExpenseExportService** | Stream Expenses for a date range to CSV without loading all rows in memory (CQ-10). | REQ-EXP-014 |

---

## 6. Bounded Context — Savings Goal  *(service: `savings-goal-service`)*

Owns: Savings Goals, Contributions (both flows), progress/projection, and goal lifecycle.
(REQ-GOAL-001…014.)

### 6.1 Aggregate: SavingsGoal

```text
«AR» SavingsGoal
 ├─ identity:        «VO» SavingsGoalId          (1──1)
 ├─ owner:           «VO» UserId
 ├─ name:            «VO» GoalName
 ├─ targetAmount:    «VO» Money                   (> 0, INR — REQ-GOAL-001)
 ├─ targetDate:      «VO» DateOnly?               (optional user intent)
 ├─ description:     «VO» Description?            (optional)
 ├─ appearance:      «VO» Appearance?             { icon, color } (optional)
 ├─ status:          «enum» GoalStatus            { ACTIVE, PAUSED, COMPLETED, ABANDONED }
 ├─ contributions:   «E»  ContributionEntry       (1───< *  — the Contribution History)
 └─ audit:           «VO» AuditTimestamps
```

| Element | Kind | Notes / Invariants |
|---------|------|--------------------|
| **SavingsGoal** | «AR» | Root. Auto-transitions to `COMPLETED` when Σ contributions ≥ `targetAmount`, emitting an in-app Notification (REQ-GOAL-011). `PAUSED` excludes the goal from projection and the active list while preserving history (REQ-GOAL-013). Manual `COMPLETED`/`ABANDONED` allowed regardless of total (REQ-GOAL-012). Deleting the goal detaches but does not delete backing Expenses (REQ-GOAL-003). |
| **ContributionEntry** | «E» | Local entity = one Contribution record in the **Contribution History**: `{ contributionEntryId, expenseRef: ExpenseId, amount: Money, date: DateOnly, source ∈ {GOAL_SCREEN, LINKED_EXPENSE} }`. **Mirrors** the backing Expense; the Expense (Expense context) is the source of truth for the amount. |
| **GoalStatus** | «enum» | `ACTIVE / PAUSED / COMPLETED / ABANDONED` (Glossary). |
| **GoalName / Money / DateOnly / Appearance** | «VO» | As defined; `targetDate` (intent) is distinct from the derived `ProjectedCompletionDate` (Glossary). |

### 6.2 Value Objects derived (computed, not stored as truth)

| «VO» | Derivation | Requirements |
|------|-----------|--------------|
| **GoalProgress** | `{ totalContributed, remainingAmount = target − total, percentAchieved }`. | REQ-GOAL-008 |
| **ProjectedCompletionDate** | From average monthly contribution rate; undefined for `PAUSED`. | REQ-GOAL-009/013 |

### 6.3 Relationships

```text
User (Identity)  1 ───< * SavingsGoal             ⇢ by UserId
SavingsGoal      1 ───< * ContributionEntry        (local entity, cascade)
ContributionEntry 1 ──── 1 Expense                 ⇢ by ExpenseId (the backing Expense in Expense ctx)
SavingsGoal (Savings Category) ── used-by ── Expense.categoryRef = Savings Category (DEFAULT)
```

### 6.4 Domain Services

| «DS» | Operation | Requirements |
|------|-----------|--------------|
| **ContributionService** | **Primary flow:** record a Contribution from the goal screen → instruct the Expense context (ACP) to create a backing Expense under the Savings Category with this `SavingsGoalId`, then append a `ContributionEntry`. **Secondary flow:** accept a "link existing Expense" signal and append/adjust the entry. Maintains the running total. | REQ-GOAL-004/005/006 |
| **ContributionReconciliationService** | React to Expense edit/delete signals (amount/date change, deletion, association removal) and update the Contribution History and total accordingly — single source of truth = the backing Expense. | REQ-GOAL-007, REQ-EXP-007/008 |
| **GoalLifecycleService** | Apply auto/manual transitions across `GoalStatus`, raise the Completed Notification (via Notification ACP), enforce pause/abandon rules, and detach (not delete) Expenses on goal deletion. | REQ-GOAL-011/012/013/003 |
| **GoalProjectionService** | Compute `GoalProgress` and `ProjectedCompletionDate`; exclude `PAUSED` goals. | REQ-GOAL-008/009 |

> **Why Contribution is not its own standalone money record.** `ContributionEntry` is a *local
> mirror* for history and totals; the authoritative amount lives in the backing **Expense**
> (Expense context). This honours the Glossary's "Contribution is realised as an Expense" rule and
> AL-1 (no shared tables) — the two contexts collaborate via Ids and the ACP, in **Partnership**.

---

## 7. Bounded Context — Budget  *(service: `budget-service`)*

Owns: per-Category/overall budgets, rollover, and threshold breach detection. (REQ-BUD-001…007,
REQ-NOTIF-002.)

### 7.1 Aggregate: Budget

```text
«AR» Budget
 ├─ identity:        «VO» BudgetId               (1──1)
 ├─ owner:           «VO» UserId
 ├─ scope:           «VO» BudgetScope             { OVERALL | CATEGORY(categoryRef: CategoryId ⇢ by Id) }
 ├─ limit:           «VO» Money                   (> 0, INR — the amount set)
 ├─ period:          «enum» BudgetPeriodType      { WEEKLY, MONTHLY }
 ├─ active:          boolean                       (activate/deactivate without delete — REQ-BUD-002)
 ├─ rolloverEnabled: boolean                       (REQ-BUD-003)
 ├─ periods:         «E»  BudgetPeriodLedger       (1───< *  — one ledger per Budget Period)
 └─ audit:           «VO» AuditTimestamps
```

```text
«E» BudgetPeriodLedger                            (local entity; one per concrete Budget Period)
 ├─ periodWindow:    «VO» PeriodWindow             { startDate, endDate }
 ├─ carriedIn:       «VO» Money                    (rollover from prior period — REQ-BUD-003)
 ├─ spent:           «VO» Money                    (derived from Expenses; see ACP note)
 └─ alertsFired:     «VO» FiredThresholdSet        { EIGHTY_PERCENT?, EXCEEDED? }  (once-per-period — REQ-BUD-006)
```

| Element | Kind | Notes / Invariants |
|---------|------|--------------------|
| **Budget** | «AR» | Root. Either `OVERALL` or scoped to a `CategoryId` (REQ-BUD-001). Inactive Budgets raise no alerts (REQ-BUD-002). |
| **BudgetPeriodLedger** | «E» | Local entity tracking one concrete Budget Period: window, carried-in rollover, spent-to-date, and which thresholds have fired. Resets per period; rollover seeds the next ledger's `carriedIn` (REQ-BUD-003/006). |
| **BudgetScope** | «VO» | `OVERALL` or `CATEGORY(categoryRef)` — cross-context Id only (AL-1). |
| **BudgetPeriodType** | «enum» | `WEEKLY` / `MONTHLY`. |
| **BudgetThreshold** | «enum» | `EIGHTY_PERCENT`, `EXCEEDED` (Glossary). |
| **FiredThresholdSet** | «VO» | Guarantees each threshold fires **at most once per period** (REQ-BUD-006). |

> **Spent amount is derived, not owned.** Budget does **not** read the Expense schema (AL-1). It
> computes `spent` from spending data delivered via the Expense context's published interface/events
> (ACP, AL-2). The ledger stores the evaluated figure for alert idempotency.

### 7.2 Domain Services

| «DS» | Operation | Requirements |
|------|-----------|--------------|
| **BudgetAuthoringService** | Create/edit a Budget; activate/deactivate; toggle rollover. | REQ-BUD-001/002/003 |
| **BudgetEvaluationService** | On new/changed spending for a Category/period, recompute `spent`, detect 80% and exceeded **Budget Thresholds**, and fire each **once per period per threshold** via the Notification ACP (in-app + email). | REQ-BUD-004/005/006, REQ-NOTIF-002 |
| **BudgetRolloverService** | At period rollover, carry unspent amount into the next `BudgetPeriodLedger` when enabled. | REQ-BUD-003 |
| **BudgetStatusService** | Report amount set / spent / remaining / percentage used for a Budget. | REQ-BUD-007 |

---

## 8. Anti-Corruption Ports (cross-context interfaces — AL-2)

These are the **only** sanctioned channels between contexts. Each is an outbound interface the
domain depends on; the owning service implements it. No context reads another's schema (AL-1).

| Port (consumer → provider) | Purpose | Backing Requirement |
|----------------------------|---------|---------------------|
| `CategoryLookupPort` (Expense, Budget → Category) | Validate a `CategoryId` exists, is visible to the user, and permits the transaction type. | REQ-EXP-001, REQ-CAT-004 |
| `CategoryUsagePort` (Category → Expense/Income) | Check whether a Category still has transactions before deletion. | REQ-CAT-005 |
| `ContributionPort` (Savings Goal → Expense) | Create/adjust the backing Expense under the Savings Category for a Contribution. | REQ-GOAL-004 |
| `ContributionEventsPort` (Expense → Savings Goal) | Notify the goal of backing-Expense edits/deletes/association changes. | REQ-EXP-007/008, REQ-GOAL-007 |
| `SpendingFeedPort` (Budget → Expense) | Provide per-Category/period spending for budget evaluation. | REQ-BUD-004/005 |
| `NotificationPort` (Savings Goal, Budget, Expense → Notification) | Raise in-app/email Notifications (goal completed, budget breach, recurring-generation failure). | REQ-NOTIF-002/003, REQ-GOAL-011 |
| `UserDataPort` (Identity → all) | Gather per-context data for the full Data Export. | REQ-USR-011 |
| `SecureNotificationDeliveryPort` (Notification → Identity) | Resolve an opaque `deliveryRef` to a one-time URL for email verification or password reset. The raw token never leaves `user-service`; `notification-service` calls this port at email-dispatch time and embeds the returned URL in the email body. | REQ-USR-004/007, SEC-1 |

---

## 9. Cross-Cutting Domain Invariants

| # | Invariant | Source |
|---|-----------|--------|
| INV-1 | Every user-owned Aggregate Root carries a `UserId`; all reads/mutations verify ownership → **403, never 404**. | P4, SEC-3 |
| INV-2 | Aggregates reference other aggregates **only by Id Value Objects**; no cross-aggregate or cross-context object references. | AL-1 |
| INV-3 | Cross-context behaviour flows through Anti-Corruption Ports / published interfaces — never another service's repository or schema. | AL-1, AL-2 |
| INV-4 | A **Contribution**'s authoritative amount is its backing **Expense** under the **Savings Category**; the `ContributionEntry` is a derived mirror. | REQ-GOAL-004/007 |
| INV-5 | Each **Budget Threshold** fires at most once per **Budget Period**; counters reset at the next period; rollover seeds the next ledger. | REQ-BUD-006/003 |
| INV-6 | `Money` is always INR, scale-2, never a primitive; Expense/Contribution/Budget amounts are strictly positive. | Glossary, REQ-USR-002 |
| INV-7 | Domain/service methods never return `null`; absent lookups return `Optional<T>`. | CQ-2 |
| INV-8 | The domain layer holds business logic only; no controller/DTO/repository concern appears in an Aggregate, Entity, or Domain Service. | P3, CQ-1 |
| INV-9 | A **Default Category** (incl. the **Savings Category**) is non-deletable; a Category with transactions cannot be deleted until reassigned. | REQ-CAT-001/005 |
| INV-10 | A **RecurringExpense** template is a distinct aggregate from the **Occurrences** (`Expense`) it generates. | REQ-REC-001/003 |

---

## 10. Summary Tables

### 10.1 Aggregate Roots by context

| Context (service) | Aggregate Roots | Local Entities | Key Value Objects |
|-------------------|-----------------|----------------|-------------------|
| Identity & Access (`user-service`) | **User** | EmailVerification, RefreshToken | UserId, EmailAddress, PasswordHash, PersonName, UserProfile, AccountStatus |
| Category (`category-service`) | **Category** | — | CategoryId, CategoryName, CategoryType, CategoryOrigin, SystemCategoryRole, Appearance |
| Expense / Transaction (`expense-service`) | **Expense**, **RecurringExpense** | Receipt, **Tag** | ExpenseId, Money, DateOnly, PaymentMethod, TagId⇢, MerchantName, RecurrencePattern, RecurrenceBound, CategoryId⇢, SavingsGoalId⇢ |
| Savings Goal (`savings-goal-service`) | **SavingsGoal** | ContributionEntry | SavingsGoalId, GoalName, Money, GoalStatus, GoalProgress, ProjectedCompletionDate, ExpenseId⇢ |
| Budget (`budget-service`) | **Budget** | BudgetPeriodLedger | BudgetId, BudgetScope, BudgetPeriodType, BudgetThreshold, PeriodWindow, FiredThresholdSet, Money, CategoryId⇢ |

### 10.2 Domain Services by context

| Context | Domain Services |
|---------|-----------------|
| Identity & Access | RegistrationService, AuthenticationService, TokenRotationService, AccountLifecycleService |
| Category | CategoryAuthoringService, CategoryDeletionGuard |
| Expense / Transaction | ExpenseService, ReceiptService, TagManagementService, RecurringExpenseGenerator, ExpenseImportService, ExpenseExportService |
| Savings Goal | ContributionService, ContributionReconciliationService, GoalLifecycleService, GoalProjectionService |
| Budget | BudgetAuthoringService, BudgetEvaluationService, BudgetRolloverService, BudgetStatusService |

---

## 11. Notes, Scope & Assumptions

1. **Scope.** Only the five requested contexts are modelled (Identity, Category, Expense, Savings
   Goal, Budget). The remaining constitution contexts — **Income**, **Reporting & Analytics**, and
   **Notification** — are referenced only as ACP collaborators and are deferred to a later modelling
   pass. (Income mirrors Expense closely; Reporting is a read-only aggregator; Notification is the
   delivery side of alerts already produced here.)
2. **Layers are Sacred (P3/CQ-1).** This is a pure domain model. No repository methods, DTO shapes,
   controllers, or SQL appear. Persistence is referenced only abstractly ("the aggregate is loaded /
   persisted").
3. **Service Isolation (AL-1/AL-2/AL-3).** One aggregate cluster per microservice; cross-context
   references are Id Value Objects; all collaboration is via the Anti-Corruption Ports in §8. No
   shared database or cross-schema read exists anywhere in the model.
4. **Vocabulary fidelity.** All names use the [Glossary](./02-glossary.md) (e.g. `Expense` not
   "Outflow", `Source` reserved for Income, `Merchant` for Expense, `PaymentMethod.UPI`,
   `BudgetThreshold`/`BudgetPeriod`). Prohibited anti-terms are absent.
5. **Derived vs. stored.** `GoalProgress`, `ProjectedCompletionDate`, and Budget `spent` are
   **computed** Value Objects; the only stored "truth" for monetary amounts is the backing Expense
   (for Contributions) and the Budget `limit` (for budgets).
6. **Traceability.** Every aggregate, entity, service, and invariant cites the `REQ-*` IDs it
   realises, ready for the forthcoming Traceability Matrix (REQ-ID → domain element → task → test).

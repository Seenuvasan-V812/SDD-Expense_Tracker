# Ubiquitous Language Glossary — Daily Expense Application

| Field | Value |
|-------|-------|
| **Document** | 02 — Ubiquitous Language Glossary |
| **Feature** | Daily Expense Application |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Status** | Draft |
| **Created** | 2026-06-25 |
| **Author Role** | Domain-Driven Design (DDD) Expert |
| **Source Inputs** | `Daily expense tracker Requirements - updated.md`, `.specify/memory/constitution.md` (v1.1.1), `01-context-specification.md` |
| **Governing Authority** | [Daily Expense Application — Engineering Constitution](../../.specify/memory/constitution.md) |

> **Purpose.** This glossary is the **single, binding vocabulary** for the Daily Expense
> Application. Every artefact — OpenAPI contracts, Java packages/classes, database tables,
> React components, tests, UI copy, and conversation — MUST use the exact **Term** in the
> column below. The **Synonyms / Anti-Terms** column lists words that are PROHIBITED in code,
> identifiers, and contracts because they introduce ambiguity or drift from the model. The
> **Bounded Context** column names the single context that *owns* the term, per the constitution's
> §2 Bounded Contexts table. Terms shared across contexts are marked accordingly.

> **Localization is part of the language.** This is an **India-market** application. The
> operating currency is **INR (Indian Rupee, ₹)** and **UPI** is a first-class payment method
> alongside Cash, Credit/Debit Card, Net Banking, and Other. These terms are domain vocabulary,
> not implementation detail, and appear below.

---

## How to read the Anti-Terms column

- **Term** is the *only* spelling permitted in identifiers and contracts (subject to the casing
  rules in Constitution §6 Naming Conventions — e.g. entity `Expense`, table `expenses`,
  enum value `PaymentMethod.CREDIT_CARD`).
- An **Anti-Term** is a synonym that MUST NOT appear in code or contracts. Anti-terms may appear
  in this glossary *only* to forbid them. Using an anti-term in a class, field, endpoint, column,
  DTO, or component name is a review-blocking violation (Constitution CQ-3, §6).

---

## Glossary (Alphabetical)

| Term | Definition | Synonyms / Anti-Terms (PROHIBITED in code) | Bounded Context |
|------|------------|---------------------------------------------|-----------------|
| **Abandoned** | A terminal **Goal Status** the user sets manually to stop pursuing a Savings Goal, regardless of contribution total. Contribution History is preserved; the goal leaves the active list. | ❌ `Cancelled`, `Closed`, `Dropped`, `Failed`, `Archived` | Savings Goal |
| **Access Token** | A short-lived **JWT** (15-minute expiry) that authenticates each request; identity is derived from it on every call (stateless services). | ❌ `SessionId`, `AuthCookie`, ` apiKey`, `Bearer` (as a stored field), `LoginToken` | Identity & Access |
| **Active (Goal)** | A **Goal Status** for a goal currently being pursued — not Paused, Completed, or Abandoned. Included in projections and the active goals list. | ❌ `Open`, `Live`, `Ongoing`, `Running` | Savings Goal |
| **Anti-Corruption Port (ACP)** | A DDD integration pattern that translates between two bounded contexts so that each context retains its own domain model without leaking the other's vocabulary. Implemented as a dedicated adapter or port interface (Constitution AL-2). ACPs in this system include `ExternalCurrencyRatePort` and `SecureNotificationDeliveryPort` (Notification → Identity, enabling secure token delivery without raw tokens crossing service boundaries). | ❌ `Adapter` (overloaded), `Bridge`, `Gateway` (when specifically meaning ACP), `Facade`, `Wrapper` | Cross-cutting (Architecture) |
| **Budget** | A user-defined spending limit for a single **Category** or **Overall**, scoped to a **Budget Period** (Weekly or Monthly), with optional **Rollover**. Can be activated/deactivated without deletion. | ❌ `Limit`, `Cap`, `Allowance`, `SpendingPlan`, `Envelope` | Budget |
| **Budget Alert** | A notification raised when spending crosses a **Budget Threshold** (80% reached, or budget exceeded). Delivered as both an in-app notification and an email, **once per period per threshold**. | ❌ `Warning`, `BudgetWarning`, `OverspendNotice`, `Breach` (as the alert's name) | Budget (detection) → Notification (delivery) |
| **Budget Period** | The recurring window a Budget applies to: **Weekly** or **Monthly**. Threshold counters and Rollover reset at period boundaries. | ❌ `Cycle`, `Interval`, `Term`, `Window`, `Duration` | Budget |
| **Budget Threshold** | A spending level that triggers a Budget Alert: the **80%** mark and the **exceeded (100%+)** mark. Each fires at most once per period. | ❌ `Trigger`, `Tripwire`, `AlertLevel`, `Boundary` | Budget |
| **Category** | A typed classifier for transactions, owned per-context as either **Default** (system-provided, non-deletable) or **Custom** (user-created). Carries a name and is typed **Expense / Income / Both**. | ❌ `Tag` (distinct concept — see *Tag*), `Group`, `Bucket`, `Class`, `Type` (when meaning category) | Category |
| **Completed** | A **Goal Status** reached automatically when total Contributions ≥ **Target Amount** (triggers an in-app notification), or set manually by the user. | ❌ `Done`, `Achieved`, `Finished`, `Fulfilled`, `Reached` | Savings Goal |
| **Contribution** | An amount recorded toward a **Savings Goal**. It is **always materialised as an Expense** under the system **Savings Category** linked to the goal — whether recorded from the goal screen (primary flow) or by linking an existing Expense (secondary flow). Editing/deleting the backing Expense updates the goal total. | ❌ `Deposit`, `Saving` (as a noun for one entry), `Payment`, `Topup`, `Allocation`, `Funding` | Savings Goal |
| **Contribution History** | The full, ordered list of all Contributions linked to a Goal, from either flow. Preserved even when the goal is Paused or Abandoned, or when the goal is deleted (the underlying Expenses survive). | ❌ `Ledger`, `Timeline`, `Log`, `Activity` (when meaning this list) | Savings Goal |
| **Custom Category** | A **Category** created by a user with a name, icon, and color; editable and deletable by its owner. Cannot be deleted while transactions reference it (reassign first). | ❌ `UserCategory`, `PrivateCategory`, `OwnCategory` | Category |
| **Dashboard** | The aggregated monthly view: totals (income, expense, **Net Savings**), **Savings Rate**, transaction count, active Budget statuses, category breakdown, trends, top-5 categories, and active Budget Alerts. Read-only analytics; owns no transactional data. | ❌ `Home`, `Overview` (as the model name), `Summary` (as the model name), `Landing` | Reporting & Analytics |
| **Data Export** | The user-initiated download of **all** their personal data as a single file (distinct from a CSV Expense export or a Report download). Tied to account portability. | ❌ `Backup`, `Dump`, `Extract`, `Download` (generic) | Identity & Access |
| **Default Category** | A system-provided **Category** (e.g. Food, Transport, Housing, Health, Savings, Loans…) available to all users and **non-deletable**. Includes the **Savings Category** used to back Contributions. | ❌ `SystemCategory`, `BuiltinCategory`, `StandardCategory`, `Preset` | Category |
| **Effective Limit** | The derived spending cap for a **Budget** in a given period: `limit + currentLedger.carriedIn`. When **Rollover** is disabled, Effective Limit equals the stated limit. Used in threshold calculations (80% and 100%+ triggers) and the `remaining` field returned by `GET /budgets`. | ❌ `TotalLimit`, `AdjustedLimit`, `ActualLimit`, `RealLimit` | Budget |
| **Email Verification** | The post-registration step that activates an account; the account remains **inactive until verified** via a link sent to the user's inbox. | ❌ `Activation` (as the field name), `Confirmation`, `Validation` (overloaded), `EmailCheck` | Identity & Access |
| **Expense** | A record of money **out**: amount, date, Category, **Payment Method** (required), and optional description, merchant, receipt, tags, savings-goal link, and notes. The core transaction of the system. | ❌ `Outflow`, `Spending` (as the entity), `Cost`, `Payment` (as the entity), `Debit`, `Purchase` | Expense / Transaction |
| **EXIF** | **Exchangeable Image File Format** — metadata embedded in JPEG/PNG images by cameras and smartphones, potentially containing GPS coordinates, device identifiers, and timestamps. All EXIF data MUST be stripped server-side from receipt images on upload to protect user location privacy (Doc 10 §5.3 / SEC-5). | ❌ `ImageMetadata` (generic), `PhotoData`, `GpsData` (a subset only) | Expense / Transaction |
| **ExpensePrototype** | A **Value Object** embedded in a **Recurring Expense** aggregate that stores the template values — amount, category, payment method, merchant, description, and tags — copied verbatim into each generated **Occurrence**. Changing the ExpensePrototype affects only future occurrences, not past ones. | ❌ `Template` (bare), `RecurringExpenseTemplate`, `Blueprint`, `Pattern`, `Schema` | Expense / Transaction |
| **General User** | The single human actor type: an India-based individual who manages **only their own** finances. Owns all their data; foreign access returns **403, never 404**. | ❌ `Customer`, `Account` (as the person), `Member`, `Client`, `Admin`/`Owner` (no such role exists) | Identity & Access |
| **Goal Progress** | The derived state of a Savings Goal: total contributed, **Remaining Amount**, **percentage achieved**, and **Projected Completion Date**. | ❌ `Completion` (overloaded), `Status` (distinct — see *Goal Status*), `Achievement` | Savings Goal |
| **Goal Status** | The lifecycle state of a Savings Goal: **Active**, **Paused**, **Completed**, or **Abandoned**. | ❌ `State`, `Stage`, `Phase`, `Condition` | Savings Goal |
| **Income** | A record of money **in**: amount, date, Category, **Source**, and description; may be recurring. Distinct entity from Expense; never modelled as a negative Expense. | ❌ `Inflow`, `Earning`, `Revenue`, `Credit`, `Receipt` (collides — see *Receipt*), `Deposit` | Income |
| **Idempotency-Key** | A client-generated UUID sent in the `Idempotency-Key` HTTP request header for retry-sensitive write operations (e.g. `POST /expenses/contributions`, `POST /expenses/import`, recurring-expense generation). The server de-duplicates retried requests using this key within a TTL window and returns the cached response for a duplicate (Doc 11 §4.1). | ❌ `RequestId` (generic), `IdempotencyId`, `RequestKey`, `DeduplicationKey` | Cross-cutting (API contract) |
| **INR** | The application's operating currency, the **Indian Rupee (₹)**. All monetary amounts are denominated in INR by default; profile carries a preferred currency. | ❌ `Rs`, `Rupees` (in code), `Currency` (generic placeholder for the default), `USD`/hardcoded other | Identity & Access (profile) / cross-cutting |
| **Merchant** | The optional name of the party an Expense was paid to (e.g. a shop or service). Free-text on the Expense. | ❌ `Vendor`, `Payee`, `Store`, `Seller`, `Supplier` | Expense / Transaction |
| **Net Savings** | A computed Dashboard/Report figure: **total income − total expenses** for the selected period. | ❌ `Balance`, `Profit`, `Surplus`, `Difference`, `NetIncome` | Reporting & Analytics |
| **Notification** | A user-facing message in the **Notification Center**, with read/unread state. Channels include in-app and email. Covers Budget Alerts, the Weekly Digest, and recurring-generation failures. | ❌ `Alert` (when meaning the generic message), `Message`, `Toast`, `Push`, `Reminder` | Notification |
| **Notification Center** | The in-app UI surface where all **Notifications** are listed with their read/unread state. Supports: mark individual notification as read, mark all as read, and delete a notification (REQ-NOTIF-004/005). The canonical delivery target for all in-app notification channels. | ❌ `Inbox`, `NotificationList`, `AlertCenter`, `MessageCenter`, `Feed` | Notification |
| **Object Storage** | The binary blob store (MinIO in development; an S3-compatible service in production) where receipt images and data-export files are persisted. Services access Object Storage only via dedicated ports (Anti-Corruption Ports); files are **never** stored on service disk. Receipts are served via time-limited signed URLs, never as public-read objects. | ❌ `FileStorage`, `BlobStore`, `S3` (as a generic term in code), `FileServer`, `DiskStorage` | Expense / Transaction (receipts) · Identity & Access (data export) |
| **Occurrence** | A single concrete instance generated from a **Recurring Expense** / Recurring Income template on its scheduled date. Edits/deletes choose **this occurrence** or **this and all future occurrences**. | ❌ `Instance`, `Repeat`, `Event`, `Generation` | Expense / Transaction (expense) · Income (income) |
| **Overall Budget** | A Budget that limits **total** spending across all categories for a period, as opposed to a per-Category Budget. | ❌ `GlobalBudget`, `TotalBudget`, `MasterBudget`, `AllBudget` | Budget |
| **Paused (Goal)** | A **Goal Status** that excludes a goal from **Projected Completion** calculations and the active goals list while **preserving** its Contribution History. | ❌ `Suspended`, `OnHold`, `Frozen`, `Inactive` | Savings Goal |
| **Payment Method** | The means by which an Expense was paid: **UPI**, **Cash**, **Credit Card**, **Debit Card**, **Net Banking**, or **Other**. Required on every Expense. Modelled as an enum (`PaymentMethod.CREDIT_CARD`, …). | ❌ `PayType`, `Mode`, `Tender`, `Channel`, `Method` (bare) | Expense / Transaction |
| **Projected Completion Date** | The estimated date a Goal reaches its Target, derived from the **average monthly contribution rate**. Excludes Paused goals. | ❌ `ETA`, `ForecastDate`, `DueDate`, `EstimatedDate` | Savings Goal |
| **Receipt** | An image attached to an Expense (JPEG/PNG/WEBP, ≤ 5 MB) proving the purchase. Uploadable, viewable, downloadable, and deletable; stored in **Object Storage**, never on service disk. | ❌ `Attachment`, `Bill`, `Invoice`, `Image` (bare), `File` (bare), `Proof` | Expense / Transaction |
| **Recurrence Pattern** | The schedule of a recurring entry: **Daily**, **Weekly**, **Monthly**, or **Yearly**, optionally bounded by an end date or maximum occurrences. | ❌ `Frequency`, `Schedule` (bare), `Repeat`, `Cadence`, `Interval` | Expense / Transaction · Income |
| **Recurring Expense** | A template that automatically generates future Expense **Occurrences** per its Recurrence Pattern. The template is distinct from the Occurrences it produces. | ❌ `RepeatExpense`, `ScheduledExpense`, `Subscription`, `Standing` | Expense / Transaction |
| **Refresh Token** | A 7-day token that mints new Access Tokens and **rotates** on every refresh (the old one is invalidated immediately). | ❌ `LongLivedToken`, `RenewalToken`, `SessionToken`, `PersistentToken` | Identity & Access |
| **Remaining Amount** | A derived Goal figure: **Target Amount − total contributed**. Never negative once the goal is Completed. | ❌ `Outstanding`, `Balance`, `Left`, `ToGo`, `Shortfall` | Savings Goal |
| **Report** | A generated artefact for a period — **Monthly**, **Yearly**, or **Custom Range** — containing totals, category/payment-method breakdowns, day-wise summary, and transaction list. Downloadable as **PDF or CSV**. | ❌ `Statement`, `Summary` (as the entity), `Analytics` (as the entity), `Export` (collides — see *Data Export*) | Reporting & Analytics |
| **Rollover** | A Budget option where **unspent** budget from one period carries into the next. | ❌ `Carryover`, `CarryForward`, `Accrual`, `Rollback` (wrong direction) | Budget |
| **Savings Category** | The system **Default Category** automatically assigned to the Expense that backs a **Contribution**. The user never manages it manually for contributions. | ❌ `GoalCategory`, `SavingCategory`, `ContributionCategory` | Category (defined) ↔ Savings Goal (used) |
| **Savings Goal** | A user target to accumulate money: name, **Target Amount**, optional **Target Date**, description, icon/color. Tracks Contributions, Progress, and lifecycle (**Goal Status**). Deleting it detaches but does not delete its Expenses. | ❌ `Goal` (bare in code — always `SavingsGoal`), `Target` (as the entity), `Plan`, `Fund`, `Jar`, `Pot` | Savings Goal |
| **Savings Rate** | A Dashboard metric: **Net Savings as a percentage of total income** for the period. | ❌ `SaveRate`, `SavingsPercent`, `Ratio` (bare) | Reporting & Analytics |
| **Source** | The named origin of an **Income** entry (e.g. "Salary", "Freelance"). Income-only; the Expense counterpart is **Merchant**. | ❌ `Payer`, `Origin`, `From`, `IncomeSource` (redundant prefix in code) | Income |
| **Tag** | A user-defined, free-form label applied to Expenses for **cross-category** grouping (e.g. "vacation", "office"). Distinct from **Category**: an Expense has one Category but many Tags. Deleting a tag detaches it without deleting Expenses. | ❌ `Label`, `Keyword`, `Marker`, `Category` (distinct — see *Category*) | Expense / Transaction |
| **Target Amount** | The monetary goal a **Savings Goal** aims to reach (in INR). Required at goal creation. | ❌ `GoalAmount`, `TargetValue`, `Objective`, `Quota` | Savings Goal |
| **Target Date** | The optional date by which the user intends to reach a Savings Goal's Target Amount. Distinct from the derived **Projected Completion Date**. | ❌ `Deadline`, `DueDate`, `EndDate` (collides with recurrence), `GoalDate` | Savings Goal |
| **Time-Based Scheduler** | An internal system actor (e.g., a Spring `@Scheduled` task or cron job) that fires time-triggered domain events: `WeeklyDigestDueEvent` (every Monday) and recurring-expense generation events (Doc 08 §7.2). It is a pure event producer with no business state of its own. | ❌ `CronJob` (bare, in domain language), `Scheduler` (bare), `JobRunner`, `TaskRunner`, `Timer` | Cross-cutting (Infrastructure) |
| **Token Family** | The set of **Refresh Tokens** that share a common lineage (all issued from the same root token via sequential rotation). If a previously-rotated token is reused (theft detection), the entire Token Family is immediately revoked to contain the compromise (Doc 10 §2.5). Token Family ID is stored alongside each refresh-token hash. | ❌ `TokenChain`, `TokenGroup`, `RefreshFamily`, `TokenSet`, `TokenLineage` | Identity & Access |
| **Transaction** | The umbrella term for a money movement record — an **Expense** or an **Income** entry. Used when a statement applies to both; never used as a concrete persisted type in place of Expense/Income. | ❌ `Entry` (bare), `Record` (bare), `Movement`, `Activity`, `Item` | Expense / Transaction (expense) · Income (income) |
| **Transactional Outbox** | A reliability pattern where domain events are written to an `outbox` table in the **same database transaction** as the state change that produced them. A separate relay process polls the table and publishes events to the message broker, guaranteeing **at-least-once delivery** even if the process crashes after the commit (Doc 08 §1.3). | ❌ `EventQueue` (bare), `OutboxTable`, `EventLog` (overloaded), `MessageBuffer` | Cross-cutting (Infrastructure) |
| **UPI** | **Unified Payments Interface** — an India-specific **Payment Method** value (`PaymentMethod.UPI`), first-class alongside Cash, Cards, Net Banking, and Other. | ❌ `UpiPayment`, `Gpay`/`PhonePe` (brands ≠ method), `MobilePayment`, `Wallet` | Expense / Transaction |
| **Weekly Digest** | An **opt-in (default off)** summary email sent **every Monday** covering the prior week's total expenses vs. income. | ❌ `WeeklyReport`, `Newsletter`, `Summary email`, `Recap` | Notification |

---

## Bounded Context Ownership Index

Each term above is owned by exactly one of the constitution's §2 bounded contexts. Terms with a
`↔` or `→` are *defined* in one context and *consumed* in another via the owning service's API
(never via shared tables — Constitution AL-1/AL-2).

| Bounded Context (Constitution §2) | Owned Terms |
|-----------------------------------|-------------|
| **Identity & Access** | General User, Account, Access Token, Refresh Token, Email Verification, Data Export, INR (profile/preferred currency), Token Family |
| **Category** | Category, Default Category, Custom Category, Savings Category, Category Type (Expense/Income/Both) |
| **Expense / Transaction** | Expense, Transaction (umbrella), Payment Method, UPI, Merchant, Receipt, Tag, Recurring Expense, Occurrence, Recurrence Pattern, EXIF, ExpensePrototype |
| **Savings Goal** | Savings Goal, Contribution, Contribution History, Goal Status (Active/Paused/Completed/Abandoned), Goal Progress, Target Amount, Target Date, Remaining Amount, Projected Completion Date |
| **Income** | Income, Source, (Recurring Income via Recurrence Pattern / Occurrence) |
| **Budget** | Budget, Overall Budget, Budget Period, Budget Threshold, Budget Alert (detection), Rollover, Effective Limit |
| **Reporting & Analytics** | Dashboard, Report, Net Savings, Savings Rate |
| **Notification** | Notification, Budget Alert (delivery), Weekly Digest, Notification Center |
| **Cross-cutting (Architecture / Infrastructure)** | Anti-Corruption Port (ACP), Idempotency-Key, Object Storage, Time-Based Scheduler, Transactional Outbox |

---

## Cross-Context Term Discipline (DDD Notes)

These are the model's deliberate, easy-to-violate distinctions. They are binding.

1. **Expense vs. Income are separate aggregates.** Income is **never** a negative Expense. Each is
   owned by its own context (Expense/Transaction and Income respectively). *Transaction* is a
   conversational umbrella only — there is no `Transaction` entity.
2. **Contribution is not its own stored record.** A Contribution is **realised as an Expense** under
   the **Savings Category**, linked to a Goal. The Savings Goal context records the *link and
   totals*; the Expense context owns the underlying money record. Editing/deleting that Expense
   updates the Goal total (no divergent source of truth).
3. **Category ≠ Tag.** A transaction has **one** Category (a typed classifier) and **many** Tags
   (free-form cross-cutting labels). Never collapse the two.
4. **Merchant (Expense) ≠ Source (Income).** Different contexts, different terms; do not unify into a
   generic `party`.
5. **Budget Alert detection vs. delivery.** The **Budget** context detects threshold breaches; the
   **Notification** context delivers the message. The same alert must fire **once per period per
   threshold**.
6. **Target Date (user intent) ≠ Projected Completion Date (derived).** Keep both; never overwrite
   one with the other.
7. **Receipt (Expense image) ≠ Report (generated artefact) ≠ Data Export (full account download).**
   Three distinct downloadable concepts; do not name any of them `Export`/`Download` generically.

---

## Maintenance

This glossary is a living artefact subordinate to the
**[Engineering Constitution](../../.specify/memory/constitution.md)**. When a term is added,
renamed, or retired, update this file in the same change that touches the contract, code, or
spec — consistent with the constitution's Governance section. Where any term here conflicts with
the constitution's §6 Naming Conventions, the **constitution prevails**.

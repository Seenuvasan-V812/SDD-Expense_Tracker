# API Specification — Daily Expense Application

| Field | Value |
|-------|-------|
| **Document** | 07 — API Specification (REST / pseudo-OpenAPI) |
| **Feature** | Daily Expense Application |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Status** | Draft |
| **Created** | 2026-06-25 |
| **Author Role** | Lead API Designer |
| **Source Inputs** | `.specify/memory/constitution.md` (v1.1.1), `05-domain-model.md`, `06-aggregate-specifications.md` |
| **Governing Authority** | [Daily Expense Application — Engineering Constitution](../../.specify/memory/constitution.md) |
| **Vocabulary Authority** | [Ubiquitous Language Glossary](./02-glossary.md) |
| **Traceability Authority** | [Requirement Catalogue](./03-requirement-catalogue.md) |

> **Purpose.** Define the externally observable REST contract for each of the five bounded-context
> microservices. The **OpenAPI contract is the single source of truth** (P1 / API-6); this document
> is its human-readable design. Every endpoint is versioned (`/api/v1/...`, API-1), DTO-bounded
> (API-5 / AL-4 — **entities are never serialized**), ownership-checked (**403, never 404**,
> SEC-3), and uses the mandatory pagination and error envelopes (API-2 / API-3).

---

## 1. Global API Standards (apply to every endpoint)

### 1.1 Status code contract (API-4)

| Code | Meaning | When |
|------|---------|------|
| `200 OK` | Successful read or update | GET, PUT/PATCH |
| `201 Created` | Successful creation — **with `Location` header** | POST that creates a resource |
| `204 No Content` | Successful delete — **no body** | DELETE |
| `400 Bad Request` | Validation failure | Invalid/missing input (API-7) |
| `401 Unauthorized` | Not authenticated | Missing/expired Access Token |
| `403 Forbidden` | Authenticated but not authorized | **Ownership violation — never 404** (SEC-3) |
| `404 Not Found` | Resource does not exist | Unknown id the caller *could* own |
| `409 Conflict` | Business-rule conflict | Duplicate email, delete category-in-use, etc. |
| `429 Too Many Requests` | Rate limit exceeded | Auth endpoints only (SEC-4); response MUST include `Retry-After: <seconds>` — clients MUST back off for that duration before retrying |
| `500 Internal Server Error` | Unexpected failure | Unhandled error (logged with `traceId`) |

### 1.2 Authentication

All endpoints **except** `POST /api/v1/auth/register`, `POST /api/v1/auth/login`,
`POST /api/v1/auth/refresh`, `POST /api/v1/auth/forgot-password`,
`POST /api/v1/auth/reset-password`, and `GET /api/v1/auth/verify-email` require a valid
**Access Token** (JWT, 15-min) in `Authorization: Bearer <token>` (SEC-2). Identity is derived from
the token on every request; services are stateless (AL-5). Auth endpoints are rate-limited (SEC-4).

### 1.3 Mandatory pagination envelope (API-2)

Every **list** endpoint returns exactly this envelope. Query params: `page` (0-based, default `0`),
`size` (default `20`, max `100`), and `sort` (e.g. `sort=date,desc`).

```yaml
# PageResponse<T>
content:        [ <T>, ... ]   # the page of DTOs
page:           0              # current page index (0-based)
size:           20             # page size requested
totalElements:  137            # total matching records across all pages
totalPages:     7              # ceil(totalElements / size)
```

### 1.4 Mandatory uniform error envelope (API-3)

Every error response (`4xx`/`5xx`) uses exactly this shape. PII (email, name, amounts) never appears
in `message` (CQ-13).

```yaml
# ErrorResponse
timestamp:  "2026-06-25T10:15:30+05:30"   # IST, ISO-8601
status:     400                            # HTTP status code
error:      "Bad Request"                  # HTTP reason phrase
message:    "amount must be greater than 0"
path:       "/api/v1/expenses"
traceId:    "b3f1c2a4-...-9d8e"            # correlation id (P7 / CQ-12)
```

**Worked examples:**

```yaml
# 403 — ownership violation (SEC-3): another user's Expense
status: 403
error:  "Forbidden"
message: "You do not have access to this resource"
path:   "/api/v1/expenses/8f3a.../"
traceId: "..."

# 409 — duplicate registration
status: 409
error:  "Conflict"
message: "An account with this email already exists"
path:   "/api/v1/auth/register"
traceId: "..."

# 400 — validation, with field detail
status: 400
error:  "Bad Request"
message: "Validation failed"
path:   "/api/v1/expenses"
traceId: "..."
errors:                                   # optional field-level detail array
  - field: "paymentMethod"
    reason: "must be one of UPI, CASH, CREDIT_CARD, DEBIT_CARD, NET_BANKING, OTHER"
```

### 1.5 Shared DTO conventions

- **Money** is serialized as `{ "amount": "450.00", "currency": "INR" }` (string decimal, scale-2).
- **Dates** are ISO-8601 in the user's timezone (Indian locale supported).
- **Ids** are opaque UUID strings.
- Request DTOs are suffixed `...Request`; responses `...Response` (Constitution §6). Entities are
  never serialized (AL-4).

### 1.6 Mandatory security response headers (Doc 10 §1.2 / S-08)

The following headers MUST be present on **every HTTP response** (applied globally at the gateway or Spring Security filter chain — not per-endpoint):

| Header | Required value | Purpose |
|--------|---------------|---------|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Forces HTTPS; prevents protocol downgrade |
| `X-Content-Type-Options` | `nosniff` | Prevents MIME-type sniffing (also required on receipt downloads — §4.4) |
| `X-Frame-Options` | `DENY` | Blocks clickjacking via iframe embedding |
| `Referrer-Policy` | `no-referrer` | Prevents URL leakage in the `Referer` header |
| `Content-Security-Policy` | Restrictive policy for the SPA host (implementation-defined) | Blocks XSS and unauthorized script sources |

These headers are governed by Doc 10 §1.2. The implementation applies them at the infrastructure layer; individual endpoint specs do not repeat them.

---

## 2. Identity & Access API  *(`user-service`, base `/api/v1`)*

### 2.1 Endpoints

| Method | Path | Purpose | Success | Key failures | Auth | Req |
|--------|------|---------|---------|--------------|------|-----|
| POST | `/auth/register` | Register a General User (inactive until verified) | `201` + `Location` | `400`, `409` (dup email) | public (rate-limited) | REQ-USR-003/004 |
| GET | `/auth/verify-email?token=…` | Activate account via Email Verification link | `200` | `400` (bad/expired token) | public | REQ-USR-004 |
| POST | `/auth/login` | Authenticate; issue Access + Refresh tokens | `200` | `400`, `401` (bad creds / unverified), `429` | public (rate-limited) | REQ-USR-005, SEC-2/4 |
| POST | `/auth/refresh` | Rotate Refresh Token; issue new pair | `200` | `401` (invalid/reused) | public | REQ-SEC-002 |
| POST | `/auth/logout` | Revoke the session's Refresh Token (body: `LogoutRequest`) | `204` | `400` (missing body), `401` | Bearer | REQ-USR-006 |
| POST | `/auth/forgot-password` | Send time-limited reset link | `202` | `400`, `429` | public (rate-limited) | REQ-USR-007 |
| POST | `/auth/reset-password` | Reset password via link token | `200` | `400` (bad/expired token) | public | REQ-USR-007 |
| PATCH | `/users/me/password` | Change password (current required) | `200` | `400`, `401` | Bearer | REQ-USR-009 |
| GET | `/users/me` | Get own profile | `200` | `401` | Bearer | REQ-USR-008 |
| PUT | `/users/me` | Update profile (name, preferred currency, timezone, locale, Weekly Digest toggle) | `200` | `400`, `401` | Bearer | REQ-USR-008, REQ-NOTIF-001 |
| DELETE | `/users/me` | Delete own account (removes all data) | `204` | `401` | Bearer | REQ-USR-010 |
| POST | `/users/me/data-export` | Request full Data Export (single file) | `202` (async) | `401` | Bearer | REQ-USR-011 |
| GET | `/users/me/data-export/{exportId}/download` | Get a time-limited signed download URL for a completed export | `200` | `401`, `404` (unknown export), `409` (export not yet ready) | Bearer | REQ-USR-011 |

> `202 Accepted` is used for `forgot-password` (email dispatch) and `data-export` (assembled async)
> to avoid leaking existence / blocking on long work; both still use the uniform error envelope on
> failure.

### 2.2 DTOs (pseudo-OpenAPI)

```yaml
RegisterUserRequest:
  fullName:   string        # PersonName, required
  email:      string        # EmailAddress, required, unique
  password:   string        # plain in transit only; hashed BCrypt cost≥12 (SEC-1), never returned

LoginRequest:
  email:      string
  password:   string

AuthTokenResponse:
  accessToken:   string     # JWT, 15-min
  refreshToken:  string     # 7-day, rotates on refresh
  tokenType:     "Bearer"
  expiresInSec:  900

RefreshTokenRequest:
  refreshToken: string

LogoutRequest:                  # POST /auth/logout
  refreshToken: string          # the Refresh Token of the session to revoke; allows multi-session
                                # users to log out a specific session without revoking all others

UpdateProfileRequest:
  fullName:             string   # provide current value to leave unchanged
  preferredCurrency:    string   # default "INR"
  timezone:             string   # e.g. "Asia/Kolkata"
  locale:               string?  # IETF BCP 47 tag, e.g. "en-IN" (O-06); omit to leave unchanged
  weeklyDigestEnabled:  boolean? # Weekly Digest opt-in toggle (REQ-NOTIF-001 / O-05); omit to leave unchanged

ChangePasswordRequest:
  currentPassword: string
  newPassword:     string

UserProfileResponse:
  userId:               string (uuid)
  fullName:             string
  email:                string
  preferredCurrency:    string   # "INR"
  timezone:             string
  locale:               string   # IETF BCP 47 tag, e.g. "en-IN" (O-06)
  weeklyDigestEnabled:  boolean  # Weekly Digest opt-in state; default false (REQ-NOTIF-001 / O-05)
  status:               enum { INACTIVE_UNVERIFIED, ACTIVE }
  createdAt:            string (date-time)
```

> **No password field is ever present in any response DTO** (SEC-1). `passwordHash` never leaves the
> service (AL-4).

---

## 3. Category API  *(`category-service`, base `/api/v1`)*

### 3.1 Endpoints

| Method | Path | Purpose | Success | Key failures | Req |
|--------|------|---------|---------|--------------|-----|
| GET | `/categories` | List Default + own Custom categories (filter `?type=EXPENSE\|INCOME\|BOTH`) | `200` (page) | `401` | REQ-CAT-001/004 |
| GET | `/categories/{id}` | Get one category | `200` | `401`, `403` (not owner of custom), `404` | REQ-CAT-002 |
| POST | `/categories` | Create a Custom Category | `201` + `Location` | `400`, `409` (dup name) | REQ-CAT-002 |
| PUT | `/categories/{id}` | Edit own Custom Category | `200` | `400`, `403`, `404`, `409` (default not editable) | REQ-CAT-003 |
| DELETE | `/categories/{id}` | Delete own Custom Category | `204` | `403`, `404`, `409` (in use / default) | REQ-CAT-003/005 |

> Deleting a Default Category or a Custom Category that still has transactions → **`409 Conflict`**
> with `message: "Category has associated transactions; reassign them first"` (REQ-CAT-005).

### 3.2 DTOs

```yaml
CreateCategoryRequest:
  name:  string             # CategoryName, required, unique per owner
  type:  enum { EXPENSE, INCOME, BOTH }   # required
  icon:  string             # Appearance
  color: string             # hex

CategoryResponse:
  categoryId:  string (uuid)
  name:        string
  type:        enum { EXPENSE, INCOME, BOTH }
  origin:      enum { DEFAULT, CUSTOM }
  systemRole:  enum { NONE, SAVINGS }     # SAVINGS = the Savings Category
  icon:        string
  color:       string
  deletable:   boolean                    # false for DEFAULT
```

---

## 4. Expense / Transaction API  *(`expense-service`, base `/api/v1`)*

### 4.1 Expense endpoints

| Method | Path | Purpose | Success | Key failures | Req |
|--------|------|---------|---------|--------------|-----|
| GET | `/expenses` | List own Expenses (paginated, filterable, sortable) | `200` (page) | `401` | REQ-EXP-003/004/005 |
| GET | `/expenses/{id}` | Get one Expense | `200` | `401`, `403`, `404` | REQ-EXP-003 |
| POST | `/expenses` | Create an Expense | `201` + `Location` | `400`, `403` (foreign category) | REQ-EXP-001/002 |
| PUT | `/expenses/{id}` | Edit any field (incl. goal link) | `200` | `400`, `403`, `404` | REQ-EXP-006/007 |
| DELETE | `/expenses/{id}` | Delete an Expense | `204` | `403`, `404` | REQ-EXP-008 |
| POST | `/expenses/{id}/receipt` | Upload/replace Receipt (multipart) | `200` | `400` (type/size), `403`, `404` | REQ-EXP-009, SEC-5 |
| GET | `/expenses/{id}/receipt` | View/download Receipt | `200` (binary) | `403`, `404` | REQ-EXP-010 |
| DELETE | `/expenses/{id}/receipt` | Delete Receipt (Expense retained) | `204` | `403`, `404` | REQ-EXP-011 |
| POST | `/expenses/import` | Bulk CSV import (`text/csv`, ≤ 10 MB, ≤ 10 000 rows; see `CsvImport` in §4.4) | `200` (report) | `400` (size/type/row-count) | REQ-EXP-012/013 |
| GET | `/expenses/export?from=&to=` | Export Expenses for date range as CSV | `200` (CSV stream) | `400`, `401` | REQ-EXP-014 |

**List filters (REQ-EXP-004/005):** `from`, `to` (date range), `categoryId`, `paymentMethod`,
`tag`, `savingsGoalId`; **sort:** `sort=date,desc` or `sort=amount,asc`.

### 4.2 Recurring Expense endpoints

| Method | Path | Purpose | Success | Failures | Req |
|--------|------|---------|---------|----------|-----|
| GET | `/recurring-expenses` | List Recurring Expense templates & schedules | `200` (page) | `401` | REQ-REC-006 |
| POST | `/recurring-expenses` | Create a Recurring Expense template | `201` + `Location` | `400` | REQ-REC-001/002 |
| PUT | `/recurring-expenses/{id}?scope=THIS\|THIS_AND_FUTURE` | Edit this Occurrence or this+future | `200` | `400`, `403`, `404` | REQ-REC-004 |
| DELETE | `/recurring-expenses/{id}?scope=THIS\|THIS_AND_FUTURE` | Delete this Occurrence or this+future | `204` | `403`, `404` | REQ-REC-005 |

### 4.3 Tag endpoints

| Method | Path | Purpose | Success | Failures | Req |
|--------|------|---------|---------|----------|-----|
| GET | `/tags` | List own Tags | `200` (page) | `401` | REQ-TAG-001 |
| POST | `/tags` | Create a Tag | `201` + `Location` | `400`, `409` | REQ-TAG-001 |
| PUT | `/tags/{id}` | Rename a Tag | `200` | `400`, `403`, `404` | REQ-TAG-001 |
| DELETE | `/tags/{id}` | Delete a Tag (detached from Expenses, not deleting them) | `204` | `403`, `404` | REQ-TAG-003 |

### 4.4 DTOs

```yaml
CreateExpenseRequest:
  amount:        Money       # { amount:"450.00", currency:"INR" }, > 0, required
  date:          string (date)        # required
  categoryId:    string (uuid)        # required; must be EXPENSE/BOTH and visible
  paymentMethod: enum { UPI, CASH, CREDIT_CARD, DEBIT_CARD, NET_BANKING, OTHER }  # required
  description:   string?              # optional
  merchant:      string?              # optional (Merchant; NOT "vendor"/"payee")
  notes:         string?              # optional
  tags:          [string]?            # optional Tag names/ids
  savingsGoalId: string (uuid)?       # optional; presence ⇒ Contribution (category forced to Savings)

ExpenseResponse:
  expenseId:      string (uuid)
  amount:         Money
  date:           string (date)
  categoryId:     string (uuid)
  paymentMethod:  enum {...}
  description:    string?
  merchant:       string?
  notes:          string?
  tags:           [string]
  hasReceipt:     boolean
  savingsGoalId:  string (uuid)?
  recurringExpenseId: string (uuid)?
  createdAt:      string (date-time)
  updatedAt:      string (date-time)

ReceiptUpload (multipart/form-data):
  file: binary               # JPEG/PNG/WEBP only, ≤ 5 MB (SEC-5) → else 400
                             # Type is determined by magic-byte / content sniffing — client-supplied
                             # Content-Type is NOT trusted (Doc 10 §5.2 / S-05)

ReceiptDownloadResponse:     # GET /expenses/{id}/receipt — mandatory server-enforced response headers (S-04, Doc 10 §5.3)
  # Content-Type:        image/jpeg | image/png | image/webp   (matches stored mime_type)
  # Content-Disposition: attachment; filename="receipt.<ext>"  (forces download; prevents inline HTML/script interpretation)
  # X-Content-Type-Options: nosniff                           (browser must not re-interpret the content type)
  # Body: binary image bytes streamed directly from object storage (MinIO)

CsvImport (multipart/form-data):  # POST /expenses/import — server-side constraints (S-03, Doc 10 §5.5)
  file: binary               # MUST be text/csv; validated server-side, NOT by client Content-Type
                             # Max size: 10 MB → else 400; Max rows: 10 000 → else 400 (CQ-10)
                             # Leading formula chars (=, +, -, @, \t, \r) stripped from every cell value
                             # Caller userId (from Access Token) applied to all rows; CSV userId column ignored
  # --- CSV Column Schema (REQ-EXP-012/013) — header row REQUIRED; column order is flexible ---
  # Column           Required  Values / Notes
  # date             Yes       ISO-8601 date, e.g. "2026-06-25"
  # amount           Yes       Positive decimal string, e.g. "450.00"
  # currency         No        ISO-4217 code; defaults to "INR" if blank or absent
  # categoryName     Yes       Case-insensitive match against Default or Custom Category names visible to the caller
  # paymentMethod    Yes       One of: UPI, CASH, CREDIT_CARD, DEBIT_CARD, NET_BANKING, OTHER
  # description      No        Free text
  # merchant         No        Merchant name (use "merchant", NOT "vendor" / "payee" — anti-terms)
  # notes            No        Free text
  # tags             No        Comma-separated tag names (quote the cell if it contains commas); new tags auto-created
  # savingsGoalName  No        (REQ-EXP-013) Matched case-insensitively against the caller's own Savings Goals by name:
  #                              Match found  → row linked as a Contribution; result SUCCEEDED
  #                              No match     → row imported as plain Expense; result SUCCEEDED_WITH_WARNING
  #                              Blank/absent → plain Expense; no goal association attempted
  # Unknown columns are silently ignored. Unknown paymentMethod or categoryName values → row FAILED with reason.

ImportExpensesReport:        # response of POST /expenses/import (REQ-EXP-012/013)
  totalRows:     int
  succeeded:     int
  failed:        int
  results:
    - row: 1
      status: "SUCCEEDED"
    - row: 2
      status: "FAILED"
      reason: "amount must be greater than 0"
    - row: 3
      status: "SUCCEEDED_WITH_WARNING"
      warning: "Savings Goal 'Trip' not found; goal association skipped"

CreateRecurringExpenseRequest:
  prototype:        CreateExpenseRequest      # template values
  frequency:        enum { DAILY, WEEKLY, MONTHLY, YEARLY }   # RecurrencePattern
  anchorDate:       string (date)
  endDate:          string (date)?            # RecurrenceBound (optional)
  maxOccurrences:   int?                       # RecurrenceBound (optional; null+null ⇒ indefinite)
```

### 4.5 Example — paginated list response

```yaml
# GET /api/v1/expenses?page=0&size=2&sort=date,desc  →  200 OK
content:
  - expenseId: "a1...":
    amount: { amount: "1299.00", currency: "INR" }
    date: "2026-06-21"
    categoryId: "cat-shopping"
    paymentMethod: "CREDIT_CARD"
    merchant: "Reliance Digital"
    tags: ["office","gadgets"]
    hasReceipt: true
    savingsGoalId: null
  - expenseId: "b2...":
    amount: { amount: "450.00", currency: "INR" }
    date: "2026-06-20"
    categoryId: "cat-food"
    paymentMethod: "UPI"
    tags: []
    hasReceipt: false
    savingsGoalId: null
page: 0
size: 2
totalElements: 137
totalPages: 69
```

---

## 5. Savings Goal API  *(`savings-goal-service`, base `/api/v1`)*

### 5.1 Endpoints

| Method | Path | Purpose | Success | Key failures | Req |
|--------|------|---------|---------|--------------|-----|
| GET | `/savings-goals?status=ACTIVE\|COMPLETED\|…` | List goals (active/completed shown separately) | `200` (page) | `401` | REQ-GOAL-010 |
| GET | `/savings-goals/{id}` | Goal detail: progress, projection, history | `200` | `401`, `403`, `404` | REQ-GOAL-008/009 |
| POST | `/savings-goals` | Create a Savings Goal | `201` + `Location` | `400` | REQ-GOAL-001 |
| PUT | `/savings-goals/{id}` | Edit goal | `200` | `400`, `403`, `404` | REQ-GOAL-002 |
| DELETE | `/savings-goals/{id}` | Delete goal (detaches, not deletes, Expenses) | `204` | `403`, `404` | REQ-GOAL-003 |
| POST | `/savings-goals/{id}/contributions` | Record a Contribution from the goal screen (primary flow) | `201` + `Location` | `400`, `403`, `404` | REQ-GOAL-004 |
| GET | `/savings-goals/{id}/contributions` | View Contribution History | `200` (page) | `403`, `404` | REQ-GOAL-006/008 |
| PATCH | `/savings-goals/{id}/status` | Set status: PAUSED / COMPLETED / ABANDONED; ACTIVE is only valid from PAUSED (see DTO note below) | `200` | `400`, `403`, `404`, `409` (illegal transition) | REQ-GOAL-012/013 |

> **Secondary flow (link existing Expense)** is performed via the Expense API by setting
> `savingsGoalId` on `PUT /expenses/{id}` (REQ-GOAL-005) — no separate endpoint needed.
> Editing/deleting a contribution-backing Expense is likewise done through the Expense API; the goal
> total reconciles via domain events (REQ-GOAL-007).

### 5.2 DTOs

```yaml
CreateSavingsGoalRequest:
  name:         string        # GoalName, required
  targetAmount: Money         # > 0, INR, required
  targetDate:   string (date)?    # optional user intent
  description:  string?
  icon:         string?
  color:        string?

UpdateSavingsGoalRequest:       # PUT /savings-goals/{id}
  name:         string?         # GoalName; omit to leave unchanged
  targetAmount: Money?          # > 0, INR; reducing below totalContributed auto-triggers COMPLETED (SG-INV-6)
  targetDate:   string (date)?  # pass explicit JSON null to remove the target date
  description:  string?
  icon:         string?
  color:        string?

RecordContributionRequest:    # POST /savings-goals/{id}/contributions (primary flow)
  amount: Money               # > 0, INR, required
  date:   string (date)       # required
  # NOTE: backing Expense under the Savings Category is created automatically by the service

UpdateGoalStatusRequest:
  status: enum { ACTIVE, PAUSED, COMPLETED, ABANDONED }
  # Allowed transitions (all others → 409 Conflict):
  #   ACTIVE    → PAUSED, COMPLETED, ABANDONED
  #   PAUSED    → ACTIVE (ResumeSavingsGoalCommand), ABANDONED
  #   COMPLETED → no manual transition via this endpoint; reverts to ACTIVE only via internal
  #               ReconcileContributionCommand when a backing-Expense edit/delete drops
  #               totalContributed below targetAmount (SG-INV-3, SG-INV-6) — not a direct API call
  #   ABANDONED → terminal; no transitions permitted

SavingsGoalResponse:
  savingsGoalId:          string (uuid)
  name:                   string
  targetAmount:           Money
  targetDate:             string (date)?
  status:                 enum { ACTIVE, PAUSED, COMPLETED, ABANDONED }
  totalContributed:       Money
  remainingAmount:        Money          # max(0, target − total)
  percentAchieved:        number          # 0..100
  projectedCompletionDate: string (date)? # null when PAUSED
  icon:                   string?
  color:                  string?

ContributionEntryResponse:
  contributionEntryId: string (uuid)
  expenseId:           string (uuid)      # the backing Expense (source of truth)
  amount:              Money
  date:                string (date)
  source:              enum { GOAL_SCREEN, LINKED_EXPENSE }
```

---

## 6. Budget API  *(`budget-service`, base `/api/v1`)*

### 6.1 Endpoints

| Method | Path | Purpose | Success | Key failures | Req |
|--------|------|---------|---------|--------------|-----|
| GET | `/budgets` | List own Budgets with current status | `200` (page) | `401` | REQ-BUD-007 |
| GET | `/budgets/{id}` | Budget status: set / spent / remaining / % used | `200` | `401`, `403`, `404` | REQ-BUD-007 |
| POST | `/budgets` | Create a Budget (CATEGORY or OVERALL) | `201` + `Location` | `400` (limit ≤ 0), `403` (foreign category) | REQ-BUD-001 |
| PUT | `/budgets/{id}` | Edit limit / period / scope | `200` | `400`, `403`, `404` | REQ-BUD-001 |
| PATCH | `/budgets/{id}/activation` | Activate / deactivate (no delete) | `200` | `400`, `403`, `404` | REQ-BUD-002 |
| PATCH | `/budgets/{id}/rollover` | Enable / disable Rollover | `200` | `400`, `403`, `404` | REQ-BUD-003 |
| DELETE | `/budgets/{id}` | Delete a Budget | `204` | `403`, `404` | REQ-BUD-001 |

> Budget **threshold breach alerts** (80% / exceeded) are not a client endpoint — they are produced
> internally by `BudgetEvaluationService` from spending events and delivered via the Notification
> service (REQ-BUD-004/005/006). Clients read breach state via the Notification API and via
> `GET /budgets/{id}` status.

### 6.2 DTOs

```yaml
CreateBudgetRequest:
  scope:           enum { OVERALL, CATEGORY }
  categoryId:      string (uuid)?     # required iff scope = CATEGORY
  limit:           Money              # > 0, INR, required (a Budget cannot have a non-positive limit)
  period:          enum { WEEKLY, MONTHLY }
  rolloverEnabled: boolean

UpdateBudgetRequest:            # PUT /budgets/{id}
  limit:           Money?       # > 0, INR (BUD-INV-1); omit to leave unchanged
  period:          enum { WEEKLY, MONTHLY }?
  scope:           enum { OVERALL, CATEGORY }?
  categoryId:      string (uuid)?   # required when scope changes to CATEGORY; null/absent for OVERALL

BudgetStatusResponse:
  budgetId:        string (uuid)
  scope:           enum { OVERALL, CATEGORY }
  categoryId:      string (uuid)?
  limit:           Money              # amount set
  period:          enum { WEEKLY, MONTHLY }
  active:          boolean
  rolloverEnabled: boolean
  periodWindow:    { startDate: string, endDate: string }
  carriedIn:       Money              # rollover carried into this period
  spent:           Money              # amount spent this period
  remaining:       Money              # effectiveLimit − spent
  percentUsed:     number             # 0..N
  firedThresholds: [enum { EIGHTY_PERCENT, EXCEEDED }]   # which alerts already fired this period

ActivationRequest:   { active: boolean }
RolloverRequest:     { rolloverEnabled: boolean }
```

---

## 7. Cross-Cutting Notes

### 7.1 Ownership everywhere (SEC-3 / P4)

Every `/{id}` path on `expenses`, `categories` (custom), `savings-goals`, `budgets`, `tags`, and
`recurring-expenses` verifies the resource's `userId` equals the caller's. A mismatch returns
**`403`, never `404`** — encoded in every endpoint's "Key failures" column above.

### 7.2 Contract is law (P1 / API-6)

These shapes are the design intent; the **generated OpenAPI document is authoritative**. Any drift
between code and the published spec fails the CI contract diff. Hand-written API docs are prohibited.

### 7.3 DTO boundary (API-5 / AL-4)

No JPA entity is ever serialized. Each request/response above is a dedicated DTO. Cross-context
references appear as **Ids** only (e.g. `categoryId`, `savingsGoalId`); a service never returns
another context's entity (AL-1).

### 7.4 Endpoint inventory

| Context (service) | Endpoints |
|-------------------|-----------|
| Identity & Access (`user-service`) | 13 |
| Category (`category-service`) | 5 |
| Expense / Transaction (`expense-service`) | 10 + 4 (recurring) + 4 (tags) = 18 |
| Savings Goal (`savings-goal-service`) | 8 |
| Budget (`budget-service`) | 7 |
| **Total** | **51** |

### 7.5 Assumptions

1. **`202 Accepted`** for `forgot-password` and `data-export` reflects async/non-leaking behaviour;
   all other creates use `201` + `Location` and deletes use `204` per API-4.
2. **Secondary contribution flow reuses the Expense API** (`PUT /expenses/{id}` with `savingsGoalId`)
   rather than introducing a redundant endpoint — keeping a single write path per the domain model.
3. **Budget alerts are event-driven**, not polled via a dedicated endpoint; clients observe them
   through the Notification API (a separate context, deferred) and Budget status.
4. **Notification, Income, and Reporting/Analytics APIs** are out of scope for this document (only
   the five requested contexts), and are noted for a follow-up API spec; cross-references to them
   here (e.g. budget alerts → Notification) describe internal event flow, not client endpoints.
5. **Money is a structured object** (`amount` + `currency`) to keep INR explicit and avoid float
   ambiguity, consistent with the `Money` Value Object (Glossary / aggregate specs).

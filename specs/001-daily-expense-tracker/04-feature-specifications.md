# Feature Specifications (BDD) — Daily Expense Application

| Field | Value |
|-------|-------|
| **Document** | 04 — Feature Specifications (BDD / Gherkin) |
| **Feature** | Daily Expense Application |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Status** | Draft |
| **Created** | 2026-06-25 |
| **Author Role** | QA Automation Architect |
| **Source Inputs** | `03-requirement-catalogue.md`, `02-glossary.md` |
| **Governing Authority** | [Daily Expense Application — Engineering Constitution](../../.specify/memory/constitution.md) (v1.1.2) |
| **Vocabulary Authority** | [Ubiquitous Language Glossary](./02-glossary.md) |
| **Traceability Authority** | [Requirement Catalogue](./03-requirement-catalogue.md) |

> **Purpose.** Executable-style acceptance specifications for the four **core flows** using strict
> `Given / When / Then` Gherkin. Each `Scenario` is tagged with the **Trace ID(s)** it verifies from
> the [Requirement Catalogue](./03-requirement-catalogue.md). Vocabulary follows the
> [Glossary](./02-glossary.md) verbatim (e.g. *Expense*, *Contribution*, *Savings Goal*,
> *Payment Method*, *UPI*, *Budget Alert*, *Budget Threshold*, *Access Token*).

> **Selected flows (per request):**
> 1. **User Registration / Authentication** — `Feature: User Registration & Authentication`
> 2. **Expense Creation (incl. Receipt Upload)** — `Feature: Expense Creation and Receipt Management`
> 3. **Savings Goal Contributions** — `Feature: Savings Goal Contributions`
> 4. **Budget Threshold Breaches** — `Feature: Budget Threshold Breach Alerts`

---

## 1. Conventions

### 1.1 Tagging

| Tag | Meaning |
|-----|---------|
| `@REQ-XXX-NNN` | The Requirement Catalogue Trace ID(s) the scenario verifies. A scenario may carry several. |
| `@happy-path` | Verifies the primary successful flow. |
| `@failure-path` | Verifies a critical failure: validation, unauthorized, conflict, not-found. |
| `@security` | Exercises an authentication/authorization/validation control (Constitution §8 / SEC-*). |
| `@boundary` | Exercises a threshold/edge condition (limits, exactly-at-threshold, once-per-period). |

### 1.2 Status-code expectations (per REQ-API-003)

`200` read/update · `201` create (+ `Location` header) · `204` delete · `400` validation ·
`401` unauthenticated · `403` unauthorized (ownership — **403, never 404**, per REQ-SEC-003) ·
`404` not found · `409` business-rule conflict · `500` unexpected.

### 1.3 Shared backgrounds

Common preconditions are factored into `Background` blocks per feature to keep scenarios focused.

---

## 2. Feature: User Registration & Authentication

```gherkin
@identity-access
Feature: User Registration & Authentication
  As a General User in India
  I want to register, verify my email, and authenticate securely
  So that only I can access my own financial data

  Background:
    Given the Daily Expense Application is available
    And the operating currency is INR

  # ---------------------------------------------------------------------------
  # Registration
  # ---------------------------------------------------------------------------

  @happy-path @REQ-USR-003 @REQ-USR-004
  Scenario: General User registers with valid details and receives a verification email
    Given no account exists for email "asha@example.in"
    When the General User registers with full name "Asha Rao", email "asha@example.in", and a valid password
    Then the account is created in an inactive state
    And an Email Verification message is sent to "asha@example.in"
    And the response status is 201

  @failure-path @REQ-USR-004 @security
  Scenario: An unverified account cannot log in
    Given an account exists for email "asha@example.in" that is inactive until verified
    When the General User attempts to log in with the correct email and password
    Then login is rejected because the account is not yet verified
    And no Access Token is issued

  @happy-path @REQ-USR-004
  Scenario: General User activates the account via the verification link
    Given an inactive account exists for email "asha@example.in"
    And a valid Email Verification link has been delivered to the user's inbox
    When the General User opens the Email Verification link
    Then the account becomes active
    And the General User can subsequently log in

  @failure-path @REQ-USR-003
  Scenario: Registration is rejected for a duplicate email
    Given an account already exists for email "asha@example.in"
    When the General User registers again with email "asha@example.in"
    Then registration is rejected with a clear validation message
    And the response status is 409

  @failure-path @REQ-USR-003 @REQ-API-007 @security
  Scenario Outline: Registration is rejected for invalid input
    Given no account exists for email "<email>"
    When the General User registers with full name "<name>", email "<email>", and password "<password>"
    Then registration is rejected with a clear validation message
    And the response status is 400

    Examples:
      | name      | email             | password   | reason                 |
      |           | asha@example.in   | Str0ng!pwd | missing full name      |
      | Asha Rao  | not-an-email      | Str0ng!pwd | malformed email        |
      | Asha Rao  | asha@example.in   | weak       | password too weak      |

  @security @REQ-SEC-001
  Scenario: A registered password is never stored or returned in plain text
    Given the General User has registered with a valid password
    When the stored account record and the registration response are inspected
    Then the password appears only as a BCrypt hash with cost factor at least 12
    And the plain-text password appears in no response, log, or database column

  # ---------------------------------------------------------------------------
  # Login, tokens, logout
  # ---------------------------------------------------------------------------

  @happy-path @REQ-USR-005 @REQ-SEC-002
  Scenario: General User logs in with valid credentials and receives tokens
    Given an active account exists for email "asha@example.in"
    When the General User logs in with the correct email and password
    Then a short-lived Access Token with a 15-minute expiry is issued
    And a Refresh Token with a 7-day expiry is issued
    And the response status is 200

  @failure-path @REQ-USR-005 @security
  Scenario: Login is rejected for an incorrect password
    Given an active account exists for email "asha@example.in"
    When the General User logs in with the correct email and an incorrect password
    Then login is rejected
    And no Access Token is issued
    And the response status is 401

  @security @boundary @REQ-SEC-004
  Scenario: Repeated failed logins are rate-limited
    Given an active account exists for email "asha@example.in"
    And the auth endpoint rate limit for failed attempts has been reached
    When the General User attempts to log in again
    Then the attempt is rejected by rate limiting
    And the response status indicates the request was throttled

  @happy-path @REQ-SEC-002 @security
  Scenario: Refresh Token rotates and the old token is invalidated
    Given the General User holds a valid Refresh Token
    When the General User refreshes their session with that Refresh Token
    Then a new Access Token and a new Refresh Token are issued
    And the previous Refresh Token is invalidated immediately
    And reusing the previous Refresh Token is rejected with status 401

  @happy-path @REQ-USR-006
  Scenario: General User logs out and the session is invalidated
    Given the General User is authenticated with a valid Access Token
    When the General User logs out
    Then the current session is invalidated
    And subsequent requests with the old Access Token are rejected with status 401

  # ---------------------------------------------------------------------------
  # Ownership boundary (cross-cutting, exercised via auth)
  # ---------------------------------------------------------------------------

  @failure-path @security @REQ-SEC-003
  Scenario: Accessing another General User's resource returns 403, never 404
    Given two active General Users "Asha" and "Ravi" each own private data
    And "Asha" is authenticated with a valid Access Token
    When "Asha" requests a resource owned by "Ravi"
    Then the request is denied for ownership reasons
    And the response status is 403
    And the response status is not 404

  @failure-path @security @REQ-API-001 @REQ-SEC-003
  Scenario: An unauthenticated request to a protected resource is rejected
    Given no valid Access Token is presented
    When a request is made to a protected "/api/v1" resource
    Then the request is rejected as unauthenticated
    And the response status is 401
```

---

## 3. Feature: Expense Creation and Receipt Management

```gherkin
@expense-transaction
Feature: Expense Creation and Receipt Management
  As an authenticated General User
  I want to record Expenses with required and optional details and attach Receipts
  So that my spending is captured accurately with supporting evidence

  Background:
    Given an active General User is authenticated with a valid Access Token
    And the default Categories are available
    And amounts are denominated in INR

  # ---------------------------------------------------------------------------
  # Expense creation — happy paths
  # ---------------------------------------------------------------------------

  @happy-path @REQ-EXP-001
  Scenario: General User adds an Expense with all required fields
    Given a valid Category typed "Expense" or "Both" named "Food"
    When the General User adds an Expense with amount 450.00, date "2026-06-20", Category "Food", and Payment Method "UPI"
    Then the Expense is created and linked to the authenticated General User
    And the response status is 201
    And the response includes a Location header for the new Expense

  @happy-path @REQ-EXP-001 @REQ-EXP-002
  Scenario: General User adds an Expense with optional details
    Given a valid Category named "Shopping"
    When the General User adds an Expense with amount 1299.00, date "2026-06-21", Category "Shopping", and Payment Method "CREDIT_CARD"
    And sets description "Headphones", Merchant "Reliance Digital", and Tags "office, gadgets"
    Then the Expense is created with the supplied description, Merchant, and Tags
    And the response status is 201

  @happy-path @boundary @REQ-USR-002 @REQ-EXP-001
  Scenario Outline: General User records each supported Payment Method
    Given a valid Category named "Food"
    When the General User adds an Expense with amount 100.00, date "2026-06-22", Category "Food", and Payment Method "<method>"
    Then the Expense is created with Payment Method "<method>"
    And the response status is 201

    Examples:
      | method      |
      | UPI         |
      | CASH        |
      | CREDIT_CARD |
      | DEBIT_CARD  |
      | NET_BANKING |
      | OTHER       |

  # ---------------------------------------------------------------------------
  # Expense creation — failure paths
  # ---------------------------------------------------------------------------

  @failure-path @REQ-EXP-001 @REQ-API-007 @security
  Scenario Outline: Adding an Expense is rejected when a required field is missing or invalid
    When the General User adds an Expense with amount "<amount>", date "<date>", Category "<category>", and Payment Method "<method>"
    Then the Expense is not created
    And the response status is 400
    And a clear validation message identifies the offending field

    Examples:
      | amount | date       | category | method | reason                 |
      |        | 2026-06-22 | Food     | UPI    | amount is required     |
      | 100.00 |            | Food     | UPI    | date is required       |
      | 100.00 | 2026-06-22 |          | UPI    | Category is required   |
      | 100.00 | 2026-06-22 | Food     |        | Payment Method required|
      | -50.00 | 2026-06-22 | Food     | UPI    | amount must be positive|

  @failure-path @REQ-EXP-001 @security @REQ-SEC-003
  Scenario: A General User cannot create an Expense under another user's Category
    Given a Custom Category "Pet Care" owned by a different General User
    When the authenticated General User adds an Expense referencing Category "Pet Care"
    Then the request is denied
    And the response status is 403
    And the response status is not 404

  @failure-path @security @REQ-SEC-003
  Scenario: A General User cannot edit an Expense they do not own
    Given an Expense owned by a different General User
    When the authenticated General User attempts to edit that Expense
    Then the request is denied for ownership reasons
    And the response status is 403

  # ---------------------------------------------------------------------------
  # Receipt upload, view/download, delete
  # ---------------------------------------------------------------------------

  @happy-path @REQ-EXP-009 @REQ-SEC-005
  Scenario: General User uploads a valid Receipt image to an existing Expense
    Given the General User owns an Expense without a Receipt
    When the General User uploads a Receipt image of type "PNG" sized 2 MB
    Then the Receipt is stored in object storage and associated with the Expense
    And the response status is 200

  @happy-path @REQ-EXP-010
  Scenario: General User views and downloads a Receipt
    Given the General User owns an Expense with a Receipt
    When the General User requests to view and download the Receipt
    Then the Receipt image is returned for download
    And the response status is 200

  @happy-path @REQ-EXP-011
  Scenario: General User deletes a Receipt from an Expense
    Given the General User owns an Expense with a Receipt
    When the General User deletes the Receipt
    Then the Receipt is removed from the Expense
    And the Expense itself is retained
    And the response status is 204

  @failure-path @boundary @REQ-SEC-005 @security
  Scenario Outline: Receipt upload is rejected for an invalid type or oversized file
    Given the General User owns an Expense
    When the General User uploads a Receipt of type "<type>" sized <size_mb> MB
    Then the upload is rejected by server-side validation
    And the response status is 400
    And the Receipt is not stored

    Examples:
      | type | size_mb | reason                          |
      | PDF  | 1       | only JPEG, PNG, WEBP are allowed |
      | GIF  | 1       | unsupported image type           |
      | PNG  | 6       | exceeds the 5 MB maximum         |

  @boundary @REQ-SEC-005
  Scenario: Receipt upload at exactly the 5 MB limit is accepted
    Given the General User owns an Expense
    When the General User uploads a Receipt image of type "JPEG" sized exactly 5 MB
    Then the Receipt is stored and associated with the Expense
    And the response status is 200

  @failure-path @security @REQ-SEC-003 @REQ-EXP-009
  Scenario: A General User cannot upload a Receipt to another user's Expense
    Given an Expense owned by a different General User
    When the authenticated General User attempts to upload a Receipt to that Expense
    Then the request is denied for ownership reasons
    And the response status is 403
```

---

## 4. Feature: Savings Goal Contributions

```gherkin
@savings-goal
Feature: Savings Goal Contributions
  As an authenticated General User
  I want to contribute toward a Savings Goal from the goal screen or by linking an Expense
  So that my goal progress reflects all contributions consistently

  Background:
    Given an active General User is authenticated with a valid Access Token
    And the system Savings Category exists and is non-deletable
    And the General User owns a Savings Goal "MacBook Pro" with Target Amount 120000.00 INR and no Contributions yet

  # ---------------------------------------------------------------------------
  # Primary flow — contribute from the goal screen
  # ---------------------------------------------------------------------------

  @happy-path @REQ-GOAL-004 @REQ-GOAL-006
  Scenario: General User records a Contribution from the goal screen
    When the General User records a Contribution of 10000.00 on "2026-06-20" from the "MacBook Pro" goal screen
    Then a backing Expense is created automatically under the Savings Category linked to the goal
    And the goal's total contributed becomes 10000.00
    And the Contribution appears in both the goal's Contribution History and the General User's Expense list
    And the response status is 201

  @happy-path @REQ-GOAL-007
  Scenario: Editing the backing Expense updates the goal's Contribution total
    Given the General User has a Contribution of 10000.00 recorded from the goal screen
    When the General User edits that backing Expense amount to 12000.00
    Then the goal's total contributed becomes 12000.00
    And the change is reflected in the Contribution History

  @happy-path @REQ-GOAL-007
  Scenario: Deleting the backing Expense reduces the goal's Contribution total
    Given the goal "MacBook Pro" has total contributed 12000.00 from a single Contribution
    When the General User deletes that backing Expense
    Then the goal's total contributed becomes 0.00
    And the Contribution no longer appears in the Contribution History

  # ---------------------------------------------------------------------------
  # Secondary flow — link an existing Expense as a Contribution
  # ---------------------------------------------------------------------------

  @happy-path @REQ-GOAL-005 @REQ-GOAL-006
  Scenario: General User links an existing Expense to a Savings Goal as a Contribution
    Given the General User owns an existing Expense of 5000.00 dated "2026-06-18"
    When the General User links that Expense to the "MacBook Pro" goal
    Then the goal's total contributed increases by 5000.00 immediately
    And the Contribution appears in the goal's Contribution History

  @happy-path @REQ-EXP-007
  Scenario: Removing a Savings Goal association decreases the goal total
    Given an Expense of 5000.00 is linked to the "MacBook Pro" goal
    When the General User removes the goal association from that Expense
    Then the goal's total contributed decreases by 5000.00
    And the Expense remains as a regular Expense

  # ---------------------------------------------------------------------------
  # Goal completion, lifecycle, and detail
  # ---------------------------------------------------------------------------

  @happy-path @boundary @REQ-GOAL-011 @REQ-NOTIF-002
  Scenario: Goal is automatically Completed when Contributions reach the Target Amount
    Given the goal "MacBook Pro" has total contributed 110000.00
    When the General User records a Contribution of 10000.00 from the goal screen
    Then the goal's total contributed becomes 120000.00
    And the Goal Status changes to Completed automatically
    And an in-app Notification informs the General User the goal is Completed

  @happy-path @boundary @REQ-GOAL-008 @REQ-GOAL-009
  Scenario: Goal detail shows progress and projected completion
    Given the goal "MacBook Pro" has Contributions totalling 60000.00
    When the General User views the goal detail
    Then the Remaining Amount is 60000.00
    And the percentage achieved is 50 percent
    And a Projected Completion Date is shown based on the average monthly contribution rate

  @happy-path @REQ-GOAL-013
  Scenario: A Paused goal is excluded from projections but retains its Contribution History
    Given the goal "MacBook Pro" has Contributions in its Contribution History
    When the General User sets the goal to Paused
    Then the goal is excluded from Projected Completion calculations and the active goals list
    And the full Contribution History remains preserved

  @happy-path @REQ-GOAL-003
  Scenario: Deleting a goal detaches but does not delete its Expenses
    Given the goal "MacBook Pro" has linked Contributions backed by Expenses
    When the General User deletes the goal
    Then those Expenses lose their goal association
    And those Expenses remain as regular Expenses under the Savings Category

  # ---------------------------------------------------------------------------
  # Failure paths
  # ---------------------------------------------------------------------------

  @failure-path @REQ-GOAL-004 @REQ-API-007 @security
  Scenario Outline: Recording a Contribution is rejected for invalid input
    When the General User records a Contribution of "<amount>" on "<date>" from the goal screen
    Then the Contribution is not recorded
    And no backing Expense is created
    And the response status is 400

    Examples:
      | amount | date       | reason                  |
      |        | 2026-06-20 | amount is required      |
      | 0.00   | 2026-06-20 | amount must be positive |
      | 5000   |            | date is required        |

  @failure-path @security @REQ-SEC-003 @REQ-GOAL-005
  Scenario: A General User cannot link an Expense to another user's Savings Goal
    Given a Savings Goal owned by a different General User
    When the authenticated General User attempts to link their own Expense to that goal
    Then the request is denied for ownership reasons
    And the response status is 403
    And the response status is not 404
```

---

## 5. Feature: Budget Threshold Breach Alerts

```gherkin
@budget
Feature: Budget Threshold Breach Alerts
  As an authenticated General User
  I want to be alerted when my spending crosses a Budget Threshold
  So that I can control spending within each Budget Period

  Background:
    Given an active General User is authenticated with a valid Access Token
    And the General User has an active Monthly Budget of 10000.00 INR for Category "Food"
    And no Budget Alert has yet been sent for the current Budget Period

  # ---------------------------------------------------------------------------
  # 80% threshold
  # ---------------------------------------------------------------------------

  @happy-path @boundary @REQ-BUD-004 @REQ-NOTIF-002
  Scenario: Reaching the 80% Budget Threshold raises a Budget Alert
    Given current spending against the "Food" Budget is 7000.00
    When the General User adds an Expense of 1000.00 in Category "Food" bringing spending to 8000.00
    Then the 80% Budget Threshold is reached
    And a Budget Alert is delivered as both an in-app Notification and an email
    And the response status for the Expense creation is 201

  @boundary @REQ-BUD-006
  Scenario: The 80% Budget Alert is sent only once per Budget Period per Budget Threshold
    Given a Budget Alert for the 80% Budget Threshold has already been sent this Budget Period
    When the General User adds another Expense in Category "Food" that keeps spending between 80% and 100%
    Then no additional 80% Budget Alert is sent

  # ---------------------------------------------------------------------------
  # Exceeded (100%+) threshold
  # ---------------------------------------------------------------------------

  @happy-path @boundary @REQ-BUD-005 @REQ-NOTIF-002
  Scenario: Exceeding the Budget raises an exceeded Budget Alert
    Given current spending against the "Food" Budget is 9500.00
    When the General User adds an Expense of 1000.00 in Category "Food" bringing spending to 10500.00
    Then the Budget is fully exceeded
    And an exceeded Budget Alert is delivered as both an in-app Notification and an email

  @boundary @REQ-BUD-004 @REQ-BUD-005 @REQ-BUD-006
  Scenario: The 80% and exceeded thresholds each fire once, independently, in the same period
    Given current spending against the "Food" Budget is 0.00
    When the General User adds an Expense of 8500.00 in Category "Food"
    Then exactly one 80% Budget Alert is sent
    When the General User adds a further Expense of 2000.00 in Category "Food"
    Then exactly one exceeded Budget Alert is sent
    And no duplicate Budget Alert is sent for either Budget Threshold this Budget Period

  @boundary @REQ-BUD-006
  Scenario: Budget Alert counters reset at the start of a new Budget Period
    Given both the 80% and exceeded Budget Alerts were sent in the previous Budget Period
    When a new Budget Period begins
    And spending again reaches the 80% Budget Threshold
    Then a fresh 80% Budget Alert is sent for the new Budget Period

  # ---------------------------------------------------------------------------
  # Budget status, activation, rollover
  # ---------------------------------------------------------------------------

  @happy-path @REQ-BUD-007
  Scenario: General User views Budget status
    Given current spending against the "Food" Budget is 8000.00
    When the General User views the "Food" Budget status
    Then the amount set is 10000.00
    And the amount spent is 8000.00
    And the amount remaining is 2000.00
    And the percentage used is 80 percent

  @happy-path @REQ-BUD-002
  Scenario: A deactivated Budget raises no Budget Alerts
    Given the "Food" Budget is deactivated
    When the General User adds Expenses in Category "Food" exceeding 10000.00
    Then no Budget Alert is sent
    And the Budget is retained for later reactivation

  @happy-path @REQ-BUD-003
  Scenario: Enabled Rollover carries unspent Budget into the next Budget Period
    Given the "Food" Budget has Rollover enabled
    And 3000.00 of the Budget was unspent in the current Budget Period
    When the next Budget Period begins
    Then the unspent 3000.00 is carried over and added to the next period's available Budget

  # ---------------------------------------------------------------------------
  # Delivery and ownership
  # ---------------------------------------------------------------------------

  # NOTE (BDD-002): REQ-NOTIF-004 and REQ-NOTIF-005 cover Notification Center read/delete behaviour
  # which belongs to the Notification bounded context (Phase 2 — deferred). The scenario below
  # validates Phase 1 budget-service outbox publication and the in-service alert deduplication
  # (BUD-INV-5); the Notification Center UI interactions are out of Phase 1 scope.
  @happy-path @REQ-NOTIF-004 @REQ-NOTIF-005
  Scenario: Budget Alerts appear in the Notification Center and can be marked read
    Given a Budget Alert has been delivered to the General User
    When the General User opens the Notification Center
    Then the unread Budget Alert is listed
    And the General User can mark it read individually or mark all as read

  @failure-path @security @REQ-SEC-003 @REQ-BUD-007
  Scenario: A General User cannot view another user's Budget status
    Given a Budget owned by a different General User
    When the authenticated General User requests that Budget's status
    Then the request is denied for ownership reasons
    And the response status is 403
    And the response status is not 404
```

---

## 6. Coverage Summary

### 6.1 Trace IDs covered by this document

| Flow | Trace IDs exercised |
|------|---------------------|
| **Registration & Auth** | REQ-USR-003, REQ-USR-004, REQ-USR-005, REQ-USR-006, REQ-USR-002, REQ-SEC-001, REQ-SEC-002, REQ-SEC-003, REQ-SEC-004, REQ-API-001, REQ-API-007 |
| **Expense Creation & Receipts** | REQ-EXP-001, REQ-EXP-002, REQ-EXP-007, REQ-EXP-009, REQ-EXP-010, REQ-EXP-011, REQ-USR-002, REQ-SEC-003, REQ-SEC-005, REQ-API-007 |
| **Savings Goal Contributions** | REQ-GOAL-003, REQ-GOAL-004, REQ-GOAL-005, REQ-GOAL-006, REQ-GOAL-007, REQ-GOAL-008, REQ-GOAL-009, REQ-GOAL-011, REQ-GOAL-013, REQ-EXP-007, REQ-NOTIF-002, REQ-SEC-003, REQ-API-007 |
| **Budget Threshold Breaches** | REQ-BUD-002, REQ-BUD-003, REQ-BUD-004, REQ-BUD-005, REQ-BUD-006, REQ-BUD-007, REQ-NOTIF-002, REQ-NOTIF-004, REQ-NOTIF-005, REQ-SEC-003 |

### 6.2 Path balance

| Flow | Happy-path scenarios | Failure / boundary scenarios |
|------|----------------------|------------------------------|
| Registration & Auth | 5 | 7 |
| Expense Creation & Receipts | 6 | 6 |
| Savings Goal Contributions | 8 | 2 |
| Budget Threshold Breaches | 6 | 5 |

### 6.3 Notes & assumptions

1. **Vocabulary fidelity.** Scenarios use Glossary terms verbatim (e.g. *Payment Method* values
   `UPI`/`CREDIT_CARD` per the enum convention, *Contribution* backed by an *Expense* under the
   *Savings Category*, *Budget Threshold*/*Budget Period*) and avoid prohibited anti-terms.
2. **Ownership is verified throughout.** Per REQ-SEC-003, every cross-user access scenario asserts
   **403, never 404** — explicitly tagged `@security`.
3. **Contribution invariant.** The Savings Goal scenarios encode the core invariant from
   REQ-GOAL-004/005/007: a Contribution is always realised as an Expense under the Savings Category,
   and edits/deletes of that Expense move the goal total — there is a single source of truth.
4. **Once-per-period alerting.** Budget scenarios explicitly cover REQ-BUD-006 (no duplicate alerts
   per Budget Threshold per Budget Period) and counter reset at a new Budget Period, since these are
   the highest-risk regressions in alerting logic.
5. **Scope.** §§2–5 cover the four original core flows. §§7–13 (added in BDD-001 remediation pass)
   extend coverage to all Phase 1 requirements. Income (REQ-INC), Dashboard (REQ-DASH), Reports
   (REQ-RPT), Notifications (REQ-NOTIF beyond §5) remain Phase 2 and are out of scope here.
6. **These are acceptance specifications, not implementation.** No framework, endpoint shape, or
   step-definition binding is prescribed here; HTTP status references denote expected externally
   observable outcomes per REQ-API-003.

---

## 7. Feature: Category Management

```gherkin
@category
Feature: Category Management
  As an authenticated General User
  I want to manage Expense categories
  So that I can organise my Expenses with meaningful classifications

  Background:
    Given an active General User is authenticated with a valid Access Token

  # ---------------------------------------------------------------------------
  # Default categories
  # ---------------------------------------------------------------------------

  @happy-path @REQ-CAT-001
  Scenario: Default categories are available to all users
    When the General User lists all available Categories
    Then the response includes a Default Category named "Savings"
    And the response includes Default Categories for "Food", "Transport", "Housing", "Health"
    And all Default Categories have a type of "EXPENSE", "INCOME", or "BOTH"

  @failure-path @REQ-CAT-001
  Scenario: Default category cannot be deleted
    Given a Default Category named "Food"
    When the General User attempts to delete the Default Category "Food"
    Then the request is rejected
    And the response status is 409

  # ---------------------------------------------------------------------------
  # Custom categories
  # ---------------------------------------------------------------------------

  @happy-path @REQ-CAT-002
  Scenario: General User creates a Custom Category
    When the General User creates a Custom Category with name "Gym", icon "dumbbell", color "#FF5733", and type "EXPENSE"
    Then the response status is 201
    And the response includes a Location header for the new Category
    And listing Categories shows "Gym" as a Custom Category of type "EXPENSE"

  @happy-path @REQ-CAT-003
  Scenario: General User renames a Custom Category
    Given the General User has a Custom Category named "Gym"
    When the General User renames it to "Fitness"
    Then the response status is 200
    And the Category is now listed as "Fitness"

  @happy-path @REQ-CAT-003
  Scenario: General User deletes an unused Custom Category
    Given the General User has a Custom Category named "Hobbies" with no associated Expenses
    When the General User deletes the Custom Category "Hobbies"
    Then the response status is 204
    And listing Categories no longer includes "Hobbies"

  @failure-path @REQ-CAT-005
  Scenario: Deleting a Category with associated Expenses is rejected
    Given the General User has a Custom Category "Gym" linked to one or more Expenses
    When the General User attempts to delete the Custom Category "Gym"
    Then the request is rejected
    And the response status is 409
    And the response body indicates the Category has associated transactions

  @happy-path @REQ-CAT-004
  Scenario: Category type constrains usage on Expenses
    Given the General User has a Category of type "INCOME"
    When the General User attempts to create an Expense with that Category
    Then the request is rejected
    And the response status is 400

  @failure-path @security @REQ-SEC-003
  Scenario: A General User cannot delete another user's Custom Category
    Given a Custom Category owned by a different General User
    When the authenticated General User attempts to delete that Category
    Then the response status is 403
    And the response status is not 404
```

---

## 8. Feature: Expense List, Edit, Delete, and CSV

```gherkin
@expense @expense-management
Feature: Expense List, Edit, Delete, and CSV Operations
  As an authenticated General User
  I want to manage my Expense list and import / export Expenses
  So that I can review, correct, and share my financial records

  Background:
    Given an active General User is authenticated with a valid Access Token
    And the General User has several Expenses across different Categories and dates

  # ---------------------------------------------------------------------------
  # List, filter, sort
  # ---------------------------------------------------------------------------

  @happy-path @REQ-EXP-003
  Scenario: General User views a paginated Expense list
    When the General User requests their Expense list
    Then the response status is 200
    And the response contains "content", "page", "size", "totalElements", "totalPages"

  @happy-path @REQ-EXP-004
  Scenario: General User filters Expenses by date range
    When the General User requests Expenses with filter from "2026-06-01" to "2026-06-30"
    Then only Expenses with a date within that range are returned

  @happy-path @REQ-EXP-004
  Scenario: General User filters Expenses by Category
    Given the General User has Expenses in Category "Food" and Category "Transport"
    When the General User filters by Category "Food"
    Then only Expenses in Category "Food" are returned

  @happy-path @REQ-EXP-004
  Scenario: General User filters Expenses by Payment Method
    When the General User filters Expenses by Payment Method "UPI"
    Then only Expenses paid via UPI are returned

  @happy-path @REQ-EXP-005
  Scenario: General User sorts Expenses by date descending
    When the General User requests their Expense list sorted by date descending
    Then Expenses are returned in descending date order

  @happy-path @REQ-EXP-005
  Scenario: General User sorts Expenses by amount ascending
    When the General User requests their Expense list sorted by amount ascending
    Then Expenses are returned in ascending amount order

  # ---------------------------------------------------------------------------
  # Edit and delete
  # ---------------------------------------------------------------------------

  @happy-path @REQ-EXP-006
  Scenario: General User edits an Expense amount
    Given the General User has an Expense with amount "500.00 INR"
    When the General User edits the Expense amount to "750.00 INR"
    Then the response status is 200
    And the Expense now has amount "750.00 INR"

  @happy-path @REQ-EXP-007
  Scenario: Editing an Expense linked to a Savings Goal updates the Contribution total
    Given the General User has an Expense of "1000.00 INR" linked to a Savings Goal with total contributed "1000.00 INR"
    When the General User edits the Expense amount to "1500.00 INR"
    Then the Savings Goal total contributed is updated to "1500.00 INR"

  @happy-path @REQ-EXP-007
  Scenario: General User removes Savings Goal link from an Expense
    Given the General User has an Expense linked to a Savings Goal
    When the General User edits the Expense to remove the Savings Goal link
    Then the Expense becomes a regular Expense
    And the Savings Goal's Contribution total decreases by the Expense amount

  @happy-path @REQ-EXP-008
  Scenario: General User deletes a standalone Expense
    Given the General User has a standalone Expense
    When the General User deletes that Expense
    Then the response status is 204
    And the Expense no longer appears in the Expense list

  @happy-path @REQ-EXP-008
  Scenario: Deleting an Expense linked to a Savings Goal reduces Contribution total
    Given the General User has an Expense of "2000.00 INR" linked to a Savings Goal with total contributed "3000.00 INR"
    When the General User deletes that Expense
    Then the Savings Goal's total contributed is "1000.00 INR"

  @failure-path @security @REQ-SEC-003
  Scenario: A General User cannot edit another user's Expense
    Given an Expense owned by a different General User
    When the authenticated General User attempts to edit that Expense
    Then the response status is 403
    And the response status is not 404

  # ---------------------------------------------------------------------------
  # CSV import / export
  # ---------------------------------------------------------------------------

  @happy-path @REQ-EXP-012
  Scenario: General User imports Expenses via CSV
    Given the General User uploads a valid CSV file containing 3 expense rows
    When the CSV import completes
    Then the response status is 200
    And the response body reports 3 rows succeeded and 0 rows failed
    And the 3 Expenses appear in the Expense list

  @happy-path @REQ-EXP-013
  Scenario: CSV import with a matching Savings Goal name links the Expense as a Contribution
    Given the General User has a Savings Goal named "MacBook Pro"
    And the General User uploads a CSV with a row where the savings_goal column is "MacBook Pro"
    When the CSV import completes
    Then that row is imported and linked to the "MacBook Pro" Savings Goal as a Contribution

  @happy-path @REQ-EXP-013
  Scenario: CSV import with a non-matching Savings Goal name imports the row with a warning
    Given the General User uploads a CSV with a row where the savings_goal column is "NonExistentGoal"
    When the CSV import completes
    Then that row is imported successfully as a regular Expense
    And the response body includes a warning that the Savings Goal association was skipped

  @failure-path @REQ-EXP-012
  Scenario: CSV import rejects a file exceeding 10 MB
    Given the General User uploads a CSV file larger than 10 MB
    When the CSV import is attempted
    Then the response status is 400

  @happy-path @REQ-EXP-014
  Scenario: General User exports Expenses for a date range as CSV
    When the General User requests a CSV export from "2026-06-01" to "2026-06-30"
    Then the response status is 200
    And the response Content-Type is "text/csv"
    And the response body contains only Expenses within the requested date range
```

---

## 9. Feature: Recurring Expenses

```gherkin
@recurring
Feature: Recurring Expenses
  As an authenticated General User
  I want to set up Recurring Expenses
  So that repeating transactions are generated automatically

  Background:
    Given an active General User is authenticated with a valid Access Token

  # ---------------------------------------------------------------------------
  # Create
  # ---------------------------------------------------------------------------

  @happy-path @REQ-REC-001
  Scenario: General User creates a Monthly Recurring Expense
    When the General User creates an Expense with frequency "MONTHLY" and anchor date "2026-07-01"
    Then the response status is 201
    And the response includes a Location header for the new Recurring Expense template
    And the template appears in the list of Recurring Expenses

  @happy-path @REQ-REC-002
  Scenario: General User creates a Recurring Expense with a maximum occurrence count
    When the General User creates a Recurring Expense with max_occurrences of 12
    Then the template is saved with max_occurrences "12" and no end_date

  @happy-path @REQ-REC-002
  Scenario: General User creates a Recurring Expense with an end date
    When the General User creates a Recurring Expense with end_date "2026-12-31"
    Then the template is saved with end_date "2026-12-31" and no max_occurrences

  @happy-path @REQ-REC-002
  Scenario: General User creates an indefinite Recurring Expense
    When the General User creates a Recurring Expense without an end date or max_occurrences
    Then the template is saved without end constraints

  # ---------------------------------------------------------------------------
  # Automatic generation
  # ---------------------------------------------------------------------------

  @happy-path @REQ-REC-003
  Scenario: System generates the next Occurrence on schedule
    Given a Monthly Recurring Expense with anchor_date "2026-07-01" and no Occurrences yet
    When the scheduler runs on or after "2026-07-01"
    Then a new Expense Occurrence is created with date "2026-07-01" and recurringExpenseId set
    And the template's next_run_date advances to "2026-08-01"

  # ---------------------------------------------------------------------------
  # Edit occurrence(s)
  # ---------------------------------------------------------------------------

  @happy-path @REQ-REC-004
  Scenario: General User edits only this Occurrence of a Recurring Expense
    Given a generated Occurrence Expense with id {occurrenceId} from a Monthly template
    When the General User edits the Occurrence with scope "THIS" changing amount to "600.00 INR"
    Then the response status is 200
    And only the target Occurrence has amount "600.00 INR"
    And the Recurring Expense template is unchanged

  @happy-path @REQ-REC-004
  Scenario: General User edits this Occurrence and all future Occurrences
    Given a generated Occurrence Expense with id {occurrenceId} from a Monthly template
    When the General User edits the Occurrence with scope "THIS_AND_FUTURE" changing amount to "800.00 INR"
    Then the response status is 200
    And the target Occurrence has amount "800.00 INR"
    And a new Recurring Expense template is created with amount "800.00 INR" from the occurrence date forward
    And the original template's end_date is set to one day before the occurrence date

  # ---------------------------------------------------------------------------
  # Delete occurrence(s)
  # ---------------------------------------------------------------------------

  @happy-path @REQ-REC-005
  Scenario: General User deletes only this Occurrence
    Given a generated Occurrence Expense with id {occurrenceId}
    When the General User deletes the Occurrence with scope "THIS"
    Then the response status is 204
    And the Occurrence Expense no longer exists
    And the Recurring Expense template and other Occurrences are unaffected

  @happy-path @REQ-REC-005
  Scenario: General User deletes this Occurrence and all future Occurrences
    Given a generated Occurrence Expense with id {occurrenceId}
    When the General User deletes the Occurrence with scope "THIS_AND_FUTURE"
    Then the response status is 204
    And the Occurrence Expense no longer exists
    And the template's end_date is set to one day before the occurrence date
    And no further Occurrences are generated from that date onward

  @failure-path @REQ-REC-004
  Scenario: Providing a non-occurrence ExpenseId to the recurring edit endpoint returns 400
    Given a regular Expense (not generated from a template) with id {regularExpenseId}
    When the General User calls PUT /recurring-expenses/{regularExpenseId}?scope=THIS
    Then the response status is 400

  # ---------------------------------------------------------------------------
  # View list
  # ---------------------------------------------------------------------------

  @happy-path @REQ-REC-006
  Scenario: General User views all Recurring Expense templates
    When the General User requests the list of Recurring Expenses
    Then the response status is 200
    And the response contains the pagination envelope
    And each entry shows the template frequency, anchor_date, next_run_date, and amount
```

---

## 10. Feature: Tag Management

```gherkin
@tag
Feature: Tag Management
  As an authenticated General User
  I want to create and manage Tags
  So that I can group Expenses across different Categories

  Background:
    Given an active General User is authenticated with a valid Access Token

  # ---------------------------------------------------------------------------
  # Create, rename, delete
  # ---------------------------------------------------------------------------

  @happy-path @REQ-TAG-001
  Scenario: General User creates a Tag
    When the General User creates a Tag named "vacation"
    Then the response status is 201
    And the Tag "vacation" appears in the Tag list

  @happy-path @REQ-TAG-001
  Scenario: General User renames a Tag
    Given the General User has a Tag named "vacation"
    When the General User renames it to "holiday"
    Then the response status is 200
    And the Tag is now listed as "holiday"

  @failure-path @REQ-TAG-001
  Scenario: Tag name must be unique per user
    Given the General User already has a Tag named "vacation"
    When the General User attempts to create another Tag named "vacation"
    Then the response status is 409

  # ---------------------------------------------------------------------------
  # Apply to Expenses
  # ---------------------------------------------------------------------------

  @happy-path @REQ-TAG-002
  Scenario: General User applies a Tag to an Expense across Categories
    Given the General User has a Tag "vacation" and two Expenses in different Categories
    When the General User applies the Tag "vacation" to both Expenses
    Then both Expenses are associated with the Tag "vacation"
    And filtering Expenses by Tag "vacation" returns both Expenses

  # ---------------------------------------------------------------------------
  # Delete Tag (detach from Expenses)
  # ---------------------------------------------------------------------------

  @happy-path @REQ-TAG-003
  Scenario: Deleting a Tag removes it from all associated Expenses without deleting them
    Given the General User has a Tag "vacation" applied to 2 Expenses
    When the General User deletes the Tag "vacation"
    Then the response status is 204
    And the Tag "vacation" no longer exists
    And both Expenses still exist but are no longer tagged with "vacation"

  @failure-path @security @REQ-SEC-003
  Scenario: A General User cannot delete another user's Tag
    Given a Tag owned by a different General User
    When the authenticated General User attempts to delete that Tag
    Then the response status is 403
    And the response status is not 404
```

---

## 11. Feature: Savings Goal Lifecycle

```gherkin
@savings-goal @goal-lifecycle
Feature: Savings Goal Lifecycle
  As an authenticated General User
  I want to create, manage, and close Savings Goals
  So that I can track and achieve specific financial targets

  Background:
    Given an active General User is authenticated with a valid Access Token

  # ---------------------------------------------------------------------------
  # Create and edit
  # ---------------------------------------------------------------------------

  @happy-path @REQ-GOAL-001
  Scenario: General User creates a Savings Goal with all optional fields
    When the General User creates a Savings Goal with name "MacBook Pro", targetAmount "150000.00 INR",
         targetDate "2027-06-01", description "For work", icon "laptop", and color "#4A90E2"
    Then the response status is 201
    And the response includes a Location header for the new Savings Goal
    And the Goal has status "ACTIVE" and totalContributed "0.00 INR"

  @happy-path @REQ-GOAL-001
  Scenario: General User creates a Savings Goal with only required fields
    When the General User creates a Savings Goal with name "Emergency Fund" and targetAmount "50000.00 INR"
    Then the response status is 201
    And the Goal has targetDate absent and status "ACTIVE"

  @happy-path @REQ-GOAL-002
  Scenario: General User updates a Savings Goal's target amount
    Given the General User has an Active Savings Goal "MacBook Pro" with targetAmount "150000.00 INR"
    When the General User updates the targetAmount to "160000.00 INR"
    Then the response status is 200
    And the Goal now has targetAmount "160000.00 INR"

  @happy-path @REQ-GOAL-002
  Scenario: General User deletes a Savings Goal
    Given the General User has a Savings Goal with no associated Expenses
    When the General User deletes the Savings Goal
    Then the response status is 204
    And the Goal no longer appears in the Savings Goal list

  # ---------------------------------------------------------------------------
  # Goal list view
  # ---------------------------------------------------------------------------

  @happy-path @REQ-GOAL-010
  Scenario: Active and Completed goals are shown separately in the list
    Given the General User has one Active goal and one Completed goal
    When the General User requests the Savings Goal list
    Then the response includes an Active goal section and a Completed goal section
    And each goal shows name, progress percentage, totalContributed, and targetAmount

  # ---------------------------------------------------------------------------
  # Goal lifecycle transitions
  # ---------------------------------------------------------------------------

  @happy-path @REQ-GOAL-011
  Scenario: Goal auto-completes when Contributions reach Target Amount
    Given the General User has an Active goal with targetAmount "10000.00 INR" and totalContributed "9000.00 INR"
    When the General User records a Contribution of "1000.00 INR"
    Then the Goal status transitions to "COMPLETED"
    And an in-app Notification is delivered for goal completion

  @happy-path @REQ-GOAL-012
  Scenario: General User manually marks a Goal as Completed
    Given the General User has an Active goal with totalContributed below targetAmount
    When the General User sets the goal status to "COMPLETED"
    Then the response status is 200
    And the Goal status is "COMPLETED"

  @happy-path @REQ-GOAL-012
  Scenario: General User marks a Goal as Abandoned
    Given the General User has an Active goal
    When the General User sets the goal status to "ABANDONED"
    Then the response status is 200
    And the Goal status is "ABANDONED"
    And the Contribution History is preserved

  @failure-path @REQ-GOAL-012
  Scenario: Attempting to reopen a Completed goal via the status API returns 409
    Given the General User has a Completed goal
    When the General User attempts to set the goal status to "ACTIVE" via the status endpoint
    Then the response status is 409

  @happy-path @REQ-GOAL-013
  Scenario: General User pauses a goal
    Given the General User has an Active goal
    When the General User sets the goal status to "PAUSED"
    Then the response status is 200
    And the Goal status is "PAUSED"
    And the Goal does not appear in the Active goals list
    And the Goal's Contribution History is preserved

  @happy-path @REQ-GOAL-013
  Scenario: General User resumes a paused goal
    Given the General User has a Paused goal
    When the General User resumes the goal
    Then the response status is 200
    And the Goal status is "ACTIVE"

  @failure-path @security @REQ-SEC-003
  Scenario: A General User cannot access another user's Savings Goal
    Given a Savings Goal owned by a different General User
    When the authenticated General User requests that Savings Goal
    Then the response status is 403
    And the response status is not 404
```

---

## 12. Feature: User Profile, Password Reset, and Account Management

```gherkin
@user-management
Feature: User Profile, Password Reset, and Account Management
  As an authenticated General User
  I want to manage my account settings and data
  So that I can control my profile, credentials, and personal data

  Background:
    Given an active General User is authenticated with a valid Access Token

  # ---------------------------------------------------------------------------
  # Password reset (unauthenticated flow)
  # ---------------------------------------------------------------------------

  @happy-path @REQ-USR-007
  Scenario: General User resets password via email link
    Given the General User is not authenticated
    When the General User submits a password-reset request with their registered email
    Then the response status is 200
    And a time-limited password-reset email is sent to that address
    When the General User follows the reset link and submits a new password
    Then the response status is 204
    And the old password no longer authenticates

  @failure-path @REQ-USR-007
  Scenario: Password-reset link is single-use
    Given the General User has already used a password-reset link once
    When the General User attempts to use the same link again
    Then the response status is 400

  # ---------------------------------------------------------------------------
  # Profile update
  # ---------------------------------------------------------------------------

  @happy-path @REQ-USR-008
  Scenario: General User updates their profile
    When the General User updates their name to "Ravi Kumar", timezone to "Asia/Kolkata", and locale to "en-IN"
    Then the response status is 200
    And the profile reflects the new name, timezone, and locale

  # ---------------------------------------------------------------------------
  # Password change
  # ---------------------------------------------------------------------------

  @happy-path @REQ-USR-009
  Scenario: General User changes their password providing current password
    When the General User submits the current password and a new password
    Then the response status is 204
    And all existing Refresh Tokens for the user are invalidated
    And the user must re-authenticate with the new password

  @failure-path @REQ-USR-009
  Scenario: Password change fails if current password is wrong
    When the General User submits an incorrect current password
    Then the response status is 400

  # ---------------------------------------------------------------------------
  # Account deletion
  # ---------------------------------------------------------------------------

  @happy-path @REQ-USR-010
  Scenario: General User deletes their account
    When the General User submits a delete-account request
    Then the response status is 204
    And the account is marked DELETED
    And all associated Expenses, Goals, Budgets, and Tokens are removed

  @failure-path @REQ-USR-010
  Scenario: Deleted account cannot be authenticated
    Given the General User has deleted their account
    When a login is attempted with the deleted account's credentials
    Then the response status is 401

  # ---------------------------------------------------------------------------
  # Data export
  # ---------------------------------------------------------------------------

  @happy-path @REQ-USR-011
  Scenario: General User requests a Data Export
    When the General User requests a Data Export
    Then the response status is 202
    And the system initiates export assembly
    When the export is ready
    Then the General User can download the export file via a time-limited URL

  @failure-path @security @REQ-SEC-003
  Scenario: A General User cannot access another user's profile or export
    Given a profile or export owned by a different General User
    When the authenticated General User requests that resource
    Then the response status is 403
    And the response status is not 404
```

---

## 13. Feature: Budget Creation

```gherkin
@budget @budget-creation
Feature: Budget Creation
  As an authenticated General User
  I want to set Budgets for Categories and overall spending
  So that I can control my expenditure within a Budget Period

  Background:
    Given an active General User is authenticated with a valid Access Token

  # ---------------------------------------------------------------------------
  # Create
  # ---------------------------------------------------------------------------

  @happy-path @REQ-BUD-001
  Scenario: General User creates a Category Budget for a Monthly period
    Given the General User has a Category "Food"
    When the General User creates a Budget for Category "Food" with limit "10000.00 INR" and period "MONTHLY"
    Then the response status is 201
    And the response includes a Location header for the new Budget
    And the Budget is active and shows spent "0.00 INR" and remaining "10000.00 INR"

  @happy-path @REQ-BUD-001
  Scenario: General User creates an Overall Budget for a Weekly period
    When the General User creates an Overall Budget with limit "5000.00 INR" and period "WEEKLY"
    Then the response status is 201
    And the Overall Budget is active with effectiveLimit "5000.00 INR"

  @failure-path @REQ-BUD-001
  Scenario: Budget with zero limit is rejected
    When the General User attempts to create a Budget with limit "0.00 INR"
    Then the response status is 400

  @failure-path @security @REQ-SEC-003
  Scenario: A General User cannot view another user's Budget
    Given a Budget owned by a different General User
    When the authenticated General User requests that Budget
    Then the response status is 403
    And the response status is not 404
```

---

## 14. Updated Coverage Summary

### 14.1 Trace IDs covered by this document (all Phase 1)

| Flow | Trace IDs exercised |
|------|---------------------|
| **Registration & Auth** | REQ-USR-001..006, REQ-SEC-001..004, REQ-API-001, REQ-API-007 |
| **Expense Creation & Receipts** | REQ-EXP-001, REQ-EXP-002, REQ-EXP-009..011, REQ-USR-002, REQ-SEC-003, REQ-SEC-005, REQ-API-007 |
| **Savings Goal Contributions** | REQ-GOAL-003..009, REQ-GOAL-011, REQ-GOAL-013, REQ-EXP-007, REQ-NOTIF-002, REQ-SEC-003, REQ-API-007 |
| **Budget Threshold Breaches** | REQ-BUD-002..007, REQ-NOTIF-002, REQ-NOTIF-004, REQ-NOTIF-005, REQ-SEC-003 |
| **Category Management** | REQ-CAT-001..005, REQ-SEC-003 |
| **Expense List, Edit, Delete, CSV** | REQ-EXP-003..008, REQ-EXP-012..014, REQ-SEC-003 |
| **Recurring Expenses** | REQ-REC-001..006 |
| **Tag Management** | REQ-TAG-001..003, REQ-SEC-003 |
| **Savings Goal Lifecycle** | REQ-GOAL-001..002, REQ-GOAL-010..013, REQ-SEC-003 |
| **User Account Management** | REQ-USR-007..011, REQ-SEC-003 |
| **Budget Creation** | REQ-BUD-001, REQ-SEC-003 |

### 14.2 Phase 1 requirements NOT covered (deferred Phase 2 or out of scope)

| Requirement set | Reason |
|-----------------|--------|
| REQ-INC-001..004 | Income bounded context — Phase 2 |
| REQ-DASH-001..006 | Reporting & Analytics — Phase 2 |
| REQ-RPT-001..005 | Reporting & Analytics — Phase 2 |
| REQ-NOTIF-001..005 (beyond alerting) | Notification bounded context — Phase 2 |
| REQ-GOAL-014 | Blocked by Dashboard deferral (O-01/O-07) |

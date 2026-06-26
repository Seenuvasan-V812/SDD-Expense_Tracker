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
| **Governing Authority** | [Daily Expense Application — Engineering Constitution](../../.specify/memory/constitution.md) (v1.1.1) |
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
5. **Scope.** Only the four requested core flows are specified here. Other catalogued requirements
   (Categories, Tags, Income, Recurring generation, Dashboard, Reports, Data Export, Accessibility)
   are out of scope for this document and remain anchored in `03-requirement-catalogue.md` for a
   later BDD pass.
6. **These are acceptance specifications, not implementation.** No framework, endpoint shape, or
   step-definition binding is prescribed here; HTTP status references denote expected externally
   observable outcomes per REQ-API-003.

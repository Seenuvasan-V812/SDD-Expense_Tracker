**Daily Expense Application — Functional Requirements & Standards**

**1\. Functional Requirements**

1.1 User Management

* A user must be able to register with their full name, email address, and password.  
* The system must send a verification email upon registration; the account remains inactive until verified.  
* A user must be able to log in with email and password.  
* A user must be able to log out, which invalidates the current session.  
* A user must be able to reset their password via a time-limited email link.  
* A user must be able to update their profile — name, preferred currency, and timezone.  
* A user must be able to change their password by providing their current password.  
* A user must be able to delete their own account. Deletion must remove all associated data.  
* A user must be able to export all their data as a single downloadable file.

1.2 Categories

* The system must provide a set of default categories (e.g. Food, Transport, Housing, Health, Entertainment, Shopping, Education, Savings, Other) that are available to all users and cannot be deleted.  
* A user must be able to create custom categories with a name, icon, and color.  
* A user must be able to edit or delete their own custom categories.  
* A category must be typed — Expense, Income, or Both.  
* Deleting a category that has associated transactions must be prevented; the user must reassign those transactions first.

1.3 Expense Management

* A user must be able to add an expense with the following details:  
  * Amount (required)  
  * Date (required)  
  * Category (required)  
  * Description (optional)  
  * Payment method — Cash, Credit Card, Debit Card, UPI, Net Banking, Other (required)  
  * Merchant name (optional)  
  * Receipt image upload (optional)  
  * Tags (optional, multiple)  
  * Savings goal (optional) — associate this expense as a contribution toward an existing savings goal  
  * Notes (optional)  
* A user must be able to view a paginated, filterable, and sortable list of their expenses.  
  * Filters: date range, category, payment method, tag, savings goal  
  * Sort: date, amount  
* A user must be able to edit any field of an existing expense.  
  * If an expense is linked to a savings goal, the user must be able to change or remove that association. The goal's contribution total must update accordingly.  
* A user must be able to delete an expense.  
  * If the expense is linked to a savings goal, deleting it must reduce the goal's contribution total accordingly.  
* A user must be able to upload a receipt image against an expense after creation.  
* A user must be able to view and download the receipt for an expense.  
* A user must be able to delete a receipt from an expense.  
* A user must be able to import expenses in bulk via a CSV file. The system must report which rows succeeded and which failed with reasons.  
  * The import format must support an optional savings goal column. If a goal name matches an existing goal, the transaction must be linked to it. If it does not match, the row must be imported successfully but with a warning that the goal association was skipped.  
* A user must be able to export their expenses for a given date range as a CSV file.

1.4 Recurring Expenses

* When adding an expense, a user must be able to mark it as recurring with a recurrence pattern: Daily, Weekly, Monthly, or Yearly.  
* A user must be able to specify an end date or maximum number of occurrences for a recurring expense; if neither is set, it recurs indefinitely.  
* The system must automatically generate the next occurrence of a recurring expense on the scheduled date.  
* A user must be able to edit a recurring expense with a choice of: edit only this occurrence, or edit this and all future occurrences.  
* A user must be able to delete a recurring expense with a choice of: delete only this occurrence, or delete this and all future occurrences.  
* A user must be able to view all recurring expense templates and their schedules.

1.5 Savings Goals

* A user must be able to create a savings goal with the following details:  
  * Name (required) — e.g. "MacBook Pro", "Emergency Fund", "Vacation"  
  * Target amount (required)  
  * Target date (optional) — by when they want to reach the goal  
  * Description (optional)  
  * Icon and color (optional, for visual identification)  
* A user must be able to edit or delete a savings goal.  
  * Deleting a goal must not delete the transactions linked to it. Those transactions must simply lose their goal association and remain as regular expenses under the Savings category.  
* Contributing to a goal (primary flow): A user must be able to record a contribution directly from the goal screen by entering an amount and a date. The system must automatically create an expense transaction in the background with the category set to the system-defined Savings category, linked to that goal. The user must not need to manually manage the category or navigate to the expense form to make a contribution.  
* Associating an existing expense to a goal (secondary flow): When adding or editing any expense transaction, a user must optionally be able to link it to a savings goal. This covers cases where the user has already recorded an expense and wants to count it as a goal contribution. The goal's contribution total must update immediately upon association.  
* Both flows must be reflected consistently — whether a contribution was made from the goal screen or linked manually from an expense, it must appear in the goal's contribution history and in the user's expense list.  
* A contribution recorded from the goal screen must be visible and editable in the expense list like any other expense. If the user edits the amount or date of that expense, the goal's contribution total must update accordingly. If the user deletes it, the goal's contribution total must decrease accordingly.  
* A user must be able to view the detail of a goal:  
  * Target amount and target date  
  * Total contributed so far  
  * Remaining amount to reach the target  
  * Percentage of goal achieved (progress bar)  
  * Projected completion date based on the average monthly contribution rate  
  * Full history of all contributions linked to the goal — whether made from the goal screen or associated from an expense  
* A user must be able to view a list of all their goals showing each goal's progress at a glance — active and completed goals shown separately.  
* When the total contributions reach or exceed the target amount, the system must automatically mark the goal as Completed and notify the user via an in-app notification.  
* A user must be able to manually mark a goal as Completed or Abandoned regardless of the current contribution total.  
* A user must be able to pause a goal. A paused goal must be excluded from projected completion calculations and from the active goals list, but its contribution history must be fully preserved.  
* The dashboard must include a Goals section showing all active goals with their name, progress bar, contributed amount, target amount, and target date if set.

1.6 Income Tracking

* A user must be able to add an income entry with: amount, date, category, source name, and description.  
* A user must be able to mark income as recurring, with the same recurrence options as expenses.  
* A user must be able to view, edit, and delete income entries.  
* Edit and delete of recurring income must follow the same "this occurrence / this and future" pattern as expenses.

1.7 Tags

* A user must be able to create, rename, and delete personal tags.  
* Tags can be applied to any expense for cross-category grouping (e.g. "vacation", "office").  
* Deleting a tag must remove it from all associated expenses but must not delete the expenses.

1.8 Budgets

* A user must be able to set a budget for a specific category or an overall budget, for a Weekly or Monthly period.  
* A user must be able to activate or deactivate a budget without deleting it.  
* A user must be able to enable budget rollover — unspent budget from a period carries over to the next.  
* The system must notify the user via in-app alert and email when spending reaches 80% of a budget.  
* The system must notify the user via in-app alert and email when a budget is fully exceeded.  
* Each alert must be sent only once per period per threshold — no repeated notifications for the same breach.  
* A user must be able to view the current status of each budget: amount set, amount spent, amount remaining, and percentage used.

1.9 Dashboard

* The dashboard must display a summary for the selected month:  
  * Total income, total expenses, and net savings  
  * Savings rate (net savings as a percentage of income)  
  * Number of transactions  
  * Active budget statuses  
* The dashboard must display a spending breakdown by category for the selected month (chart).  
* The dashboard must display a spending trend over the last 12 months (chart).  
* The dashboard must display an income vs. expense comparison over the last 6 months (chart).  
* The dashboard must display the top 5 spending categories for the current month.  
* The dashboard must display any active budget alerts.

1.10 Reports

* A user must be able to generate a Monthly Report for any past month.  
* A user must be able to generate a Yearly Report for any past year.  
* A user must be able to generate a Custom Range Report by selecting a start and end date.  
* Each report must include:  
  * Total income, total expenses, and net savings for the period  
  * Expense breakdown by category (amount and percentage)  
  * Expense breakdown by payment method  
  * Day-wise expense summary  
  * List of all transactions in the period  
* A user must be able to download any report as a PDF or CSV file.

1.11 Notifications

* The system must send a weekly spending digest email every Monday summarising the prior week's total expenses vs. income (opt-in, default off).  
* Budget breach alerts must be delivered as both an in-app notification and an email.  
* Recurring expense/income generation failures must surface as an in-app notification.  
* A user must be able to view all unread notifications in a notification center.  
* A user must be able to mark notifications as read individually or all at once.

**2\. Standardization Requirements**

2.1 API Design

* All endpoints must be versioned from day one: /api/v1/...  
* Every list endpoint must support pagination. The response envelope must always include: content, page, size, totalElements, totalPages.  
* HTTP status codes must be used correctly and consistently:  
  * 200 — successful read or update  
  * 201 — successful creation (with Location header pointing to the new resource)  
  * 204 — successful delete (no body)  
  * 400 — validation failure  
  * 401 — not authenticated  
  * 403 — authenticated but not authorized  
  * 404 — resource not found  
  * 409 — business rule conflict  
  * 500 — unexpected server error  
* Every error response must follow a single uniform structure: timestamp, status, error, message, path, traceId.  
* API documentation must be auto-generated and always up to date. No manual documentation.  
* DTOs must be used for all API input and output. Entities must never be serialized directly to the response.

2.2 Security

* Passwords must be hashed using BCrypt with a minimum cost factor of 12\. Plain-text passwords must never appear in logs, responses, or the database.  
* Authentication must use short-lived JWT access tokens (15-minute expiry) with a rotating refresh token (7-day expiry). On each refresh, the old token must be invalidated.  
* Every endpoint that accesses a resource (expense, budget, etc.) must verify that the resource belongs to the requesting user. Accessing another user's resource must return 403, not 404\.  
* All user inputs must be validated on the server side regardless of what the frontend sends.  
* Auth endpoints (login, register, forgot-password) must be rate-limited to prevent brute force.  
* File uploads must be validated for type and size. Only image formats (JPEG, PNG, WEBP) are accepted for receipts. Maximum file size is 5 MB.  
* Sensitive configuration (DB password, JWT secret, MinIO credentials, SMTP password) must never be hardcoded. They must come from environment variables or a secrets file excluded from version control.

2.3 Code Quality

* Business logic must live in the service layer. Controllers must only handle request parsing and response shaping. Repositories must only handle data access.  
* A service method must not call another service's repository directly — it must go through that service.  
* null must not be returned from any service method. Use Optional\<T\> for lookups that may find nothing.  
* All database write operations must be wrapped in a transaction. Read operations that span multiple queries must also use a read-only transaction.  
* No hardcoded string literals for constants (status values, enum strings, error messages). Define them as enums or constants.  
* Unused imports, commented-out code, and TODO comments must not be merged to the main branch.

2.4 Testing

* Every service class must have a corresponding unit test class. Business rules must be tested in isolation using mocks.  
* Every API endpoint must have an integration test that starts the real application with a real database (use Testcontainers).  
* Tests must cover both the happy path and key failure cases (invalid input, unauthorized access, not found).  
* Tests must be independent — no test must rely on the state left by another test.

2.5 Logging & Observability

* Every incoming HTTP request must be logged with: method, path, response status, response time, and a unique trace ID.  
* The trace ID must be present on every log line within that request's lifecycle (use MDC).  
* Log levels must be used correctly: DEBUG for internal flow, INFO for significant business events, WARN for recoverable problems, ERROR for unexpected failures with the full stack trace.  
* Personally identifiable information (email, name, amounts) must not appear in log messages.  
* Application health must be exposed at /actuator/health for load-balancer probing.  
* Key business metrics must be exposed for monitoring: number of expenses created, active users, budget alerts sent, report generation time.

2.6 Database

* Every table must have created\_at and updated\_at timestamps, populated automatically.  
* Queries that filter or join on a column must have an index on that column.  
* Bulk queries (reports, exports) must not load all records into memory. Use pagination or streaming.

2.9 Frontend Standards

* All API calls must go through a single Axios client instance. No component may call fetch or create its own Axios instance.  
* The Axios client must handle token refresh transparently — if an access token expires, the client retries the request with a new token without the component knowing.  
* All forms must validate input on the client side before submitting.  
* Loading, error, and empty states must be handled explicitly for every data-fetching component — no component may render with undefined data.  
* TypeScript strict mode must be enabled. any type is not permitted.  
* No hardcoded API base URLs in components. All configuration comes from environment variables.


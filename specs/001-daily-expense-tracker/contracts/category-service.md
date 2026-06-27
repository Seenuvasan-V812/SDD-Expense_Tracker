# Contract — `category-service` (Category)

**Base**: `/api/v1` | **Derived from**: [`07-api-specification.md`](../07-api-specification.md) §3.
Global rules (auth, pagination/error envelopes, status codes incl. 403-never-404, security headers) per
[`user-service.md`](./user-service.md) "Global rules".

## Endpoints (5)

| Method | Path | Purpose | Success | Key failures | Req |
|--------|------|---------|---------|--------------|-----|
| GET | `/categories` | List Default + own Custom (filter `?type=EXPENSE\|INCOME\|BOTH`) | 200 (page) | 401 | REQ-CAT-001/004 |
| GET | `/categories/{id}` | Get one category | 200 | 401, 403 (not owner of custom), 404 | REQ-CAT-002 |
| POST | `/categories` | Create a Custom Category | 201 + `Location` | 400, 409 dup name | REQ-CAT-002 |
| PUT | `/categories/{id}` | Edit own Custom Category | 200 | 400, 403, 404, 409 (DEFAULT not editable) | REQ-CAT-003 |
| DELETE | `/categories/{id}` | Delete own Custom Category | 204 | 403, 404, 409 (in use / DEFAULT) | REQ-CAT-003/005 |

> Deleting a Default Category, or a Custom Category that still has transactions, → **409 Conflict**
> (`"Category has associated transactions; reassign them first"`). In-use check goes through `CategoryUsagePort`
> (AL-2) — never a DB FK (REQ-CAT-005, INV-9).

## DTOs
- `CreateCategoryRequest{name (unique per owner), type∈{EXPENSE,INCOME,BOTH}, icon, color}`.
- `CategoryResponse{categoryId, name, type, origin∈{DEFAULT,CUSTOM}, systemRole∈{NONE,SAVINGS}, icon, color, deletable}` — `deletable=false` for DEFAULT.

## Ownership / visibility (Doc 10 §3.5)
- Default categories (`user_id=NULL`) are **readable by all**, never editable/deletable.
- Custom categories: foreign read/write → **403, never 404**.

## Anti-Corruption Ports provided
- `CategoryLookupPort` (consumed by expense/budget): validate a `CategoryId` exists, is visible to the user, and permits the transaction type. A foreign/invisible category on `POST/PUT /expenses` or `POST /budgets` → 403.
- `CategoryUsagePort` (consumed by Category's own deletion guard): check whether a Category still has transactions.

## Seed
`DefaultCategorySeeder` (ApplicationRunner) seeds 11 Default Categories on first boot including the Savings
Category (`system_role=SAVINGS`); e.g. Food, Transport, Housing, Health, Entertainment, Shopping, Education,
Savings, Loans, Credit & Debit, Third Party Payments & Other.

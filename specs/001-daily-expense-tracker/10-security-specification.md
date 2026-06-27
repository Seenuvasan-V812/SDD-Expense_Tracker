# Security & Privacy Specification — Daily Expense Application

| Field | Value |
|-------|-------|
| **Document** | 10 — Security & Privacy Specification |
| **Feature** | Daily Expense Application |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Status** | Draft |
| **Created** | 2026-06-25 |
| **Author Role** | Application Security (AppSec) Engineer |
| **Source Inputs** | `.specify/memory/constitution.md` (v1.1.2), `07-api-specification.md`, `09-data-contract-specification.md`, `03-requirement-catalogue.md` |
| **Governing Authority** | [Daily Expense Application — Engineering Constitution](../../.specify/memory/constitution.md) |
| **Vocabulary Authority** | [Ubiquitous Language Glossary](./02-glossary.md) |
| **Traceability Authority** | [Requirement Catalogue](./03-requirement-catalogue.md) |

> **Purpose.** Define the **exact, testable security and privacy controls** every backend microservice
> and the React client MUST implement. This document operationalises Constitution **§8 Security
> Standards (SEC-1…SEC-6)**, **P4 (Data Belongs to its Owner)**, **P5 (Secrets Never Touch the Repo)**,
> and **CQ-13 (Log hygiene / no PII)** into enforceable behaviour. It is the authority for the four
> mandated control areas: **(1) Authentication token lifecycle**, **(2) Authorization & resource
> ownership (403-never-404)**, **(3) PII handling & log masking**, and **(4) Input validation & receipt
> file security**. Where this document and a contract document (07/09) describe the same control, they
> MUST agree; any drift is a defect.

---

## 0. Security Requirement Map

Every control below traces to a Constitution law and a catalogued requirement.

| Area | Constitution | Requirement | Section |
|------|--------------|-------------|---------|
| Password hashing | SEC-1 | REQ-SEC-001 | §1.1 |
| Token lifecycle (JWT + rotating refresh) | SEC-2 | REQ-SEC-002 | §2 |
| Resource ownership (403, never 404) | P4 / SEC-3 | REQ-SEC-003 | §3 |
| Auth rate limiting | SEC-4 | REQ-SEC-004 | §5.4 |
| Receipt upload validation | SEC-5 | REQ-SEC-005 | §5 |
| Secrets externalised | SEC-6 / P5 | REQ-SEC-006 | §6 |
| Server-side validation (all input) | API-7 | REQ-API-007 | §5.1 |
| No PII in logs | CQ-13 | REQ-OBS-004 | §4 |
| Transparent token refresh (client) | FE-2 | REQ-FE-002 | §2.6 |
| CSV import validation & injection prevention | API-7 / SEC-5 | REQ-EXP-012/013 | §5.5 |

---

## 1. Identity Foundations

### 1.1 Password storage (SEC-1 / REQ-SEC-001)

| Control | Mandate |
|---------|---------|
| Algorithm | **BCrypt**, minimum **cost factor 12**. No other password hash is permitted. |
| Plain text | The plaintext password exists ONLY transiently in the request body over TLS. It MUST NEVER be written to logs, responses, the database, or any DTO. |
| Storage | Persisted only as `users.password_hash` (`VARCHAR(72)`, the BCrypt digest). |
| Comparison | Verification uses a constant-time BCrypt `matches`; never a string `equals`. |
| Response exposure | No response DTO contains `password` or `passwordHash` (per 07 §2.2). The hash never crosses a service boundary (AL-4). |
| Change/reset | On password change (`PATCH /users/me/password`) and reset (`POST /auth/reset-password`), **all active refresh tokens for the user are revoked** (see §2.5). |

### 1.2 Transport security (baseline for every control below)

- All client–server and service-to-object-store traffic MUST use **TLS 1.2+**; plaintext HTTP is rejected/redirected at the edge.
- Tokens, passwords, and receipts are confidential in transit; this spec assumes TLS is terminated at the gateway and re-established (or kept) to internal services.
- Recommended security response headers on every response: `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, and a restrictive `Content-Security-Policy` for the SPA host.

---

## 2. Authentication Flow — Token Lifecycle (SEC-2 / REQ-SEC-002)

Identity & Access is owned by `user-service`; all other services are **stateless** (AL-5) and derive identity solely from the Access Token on every request.

### 2.1 The two tokens

| Property | Access Token | Refresh Token |
|----------|--------------|---------------|
| Format | **JWT** (signed) | Opaque, high-entropy random string (≥ 256 bits, CSPRNG) |
| Lifetime | **15 minutes** (`expiresInSec: 900`) | **7 days** |
| Stored server-side? | **No** — stateless, validated by signature + claims | **Yes** — only the **hash** is stored in `identity_db.refresh_tokens.token_hash` |
| Purpose | Authorize every API call (`Authorization: Bearer <token>`) | Obtain a new token pair when the Access Token expires |
| Revocable? | No (short TTL is the mitigation) | Yes — `revoked_at` set on rotation/logout/reuse |
| Transmitted in | `Authorization` header | Request body (`RefreshTokenRequest`) / recommended httpOnly cookie (§2.7) |

> **Design note.** The refresh token is opaque (not a JWT) so it can be **revoked and rotated** server-side; the access token is a JWT so it can be validated **without a database hit**, keeping services stateless. The DB stores only a SHA-256 hash of the refresh token — a database disclosure does not yield usable tokens.

### 2.2 Access Token (JWT) structure

Signed with **HS256** using `JWT_SECRET` (loaded per SEC-6 / §6); RS256 with a managed key pair is an acceptable upgrade. Required claims:

```yaml
# JWT payload (claims)
sub:   "<userId UUID>"        # principal — the resource owner identity (§3)
iat:   1750000000             # issued-at (epoch seconds)
exp:   1750000900             # iat + 900s (15 min) — HARD expiry
jti:   "<uuid>"               # unique token id (traceability)
typ:   "access"               # token type discriminator (reject 'refresh' here)
```

- No PII in claims: `sub` is the opaque user UUID — **never** the email or name (§4).
- Validation on every request (every service): signature valid, `exp` not passed, `typ == "access"`, issuer/audience match. Failure → **`401 Unauthorized`** with the uniform error envelope (07 §1.4). Identity (`userId`) is taken from `sub` and placed in the security principal and MDC.

### 2.3 Login — issue the first pair

```
Client → POST /api/v1/auth/login { email, password }
user-service:
  1. Look up user by email; if absent OR BCrypt mismatch → 401 (generic "Invalid credentials")
  2. If status = INACTIVE_UNVERIFIED → 401 (account not verified)   [no info leak about which]
  3. Generate Access Token (15-min JWT) + Refresh Token (opaque, 7-day)
  4. Persist sha256(refreshToken) as a NEW row in refresh_tokens (expires_at = now+7d)
  5. Return 200 AuthTokenResponse { accessToken, refreshToken, tokenType:"Bearer", expiresInSec:900 }
```

Login is **rate-limited** (§5.4). Error messages are uniform so an attacker cannot distinguish "no such user" from "wrong password" from "unverified".

### 2.4 Refresh with rotation — the core of SEC-2

`POST /api/v1/auth/refresh` issues a **new pair** and **invalidates the presented refresh token immediately** ("rotation"). The old token is never valid again.

```
Client → POST /api/v1/auth/refresh { refreshToken }
user-service:
  1. Compute hash = sha256(refreshToken); load matching refresh_tokens row.
  2. Reject (401) if: not found · expires_at < now · revoked_at IS NOT NULL.
     └─ If revoked_at IS NOT NULL → this is a REUSE event → run §2.5 breach response.
  3. Mark the current row revoked_at = now()                     (rotation: old token dies)
  4. Issue a NEW Access Token + NEW Refresh Token (fresh 7-day)
  5. Persist sha256(newRefreshToken) as a new row (optionally store prev_id to form a "family")
  6. Return 200 AuthTokenResponse (new pair)
```

Rules:
- **One-time use.** Each refresh token can be redeemed exactly once; redemption produces a successor and revokes the predecessor.
- **No lifetime extension by activity beyond 7 days unless re-login** — the new refresh token gets a fresh 7-day window (sliding session), but a stolen-then-rotated token is dead.
- The 15-minute Access Token TTL bounds the blast radius of a leaked access token; the rotating 7-day refresh token bounds session persistence and enables theft detection.

### 2.5 Refresh-token reuse detection (theft response)

If a **revoked** refresh token is presented at `/auth/refresh`, the system assumes the token was stolen and replayed (the legitimate client already rotated it). Response:

1. **Revoke the entire Token Family** — set `revoked_at = now()` on every row in `refresh_tokens` where `family_id` matches the presented token's `family_id` (Doc 09 §2.3). Only the compromised session chain is terminated; other active sessions with different `family_id` values remain valid.
2. Return **`401 Unauthorized`**; the legitimate user is forced to log in again via that session.
3. Emit a security audit event (no PII — userId + familyId + traceId only) at `WARN`.

**Token Family assignment rule:** When a new root refresh token is issued (on login), generate a fresh `family_id` UUID. On every rotation (`POST /auth/refresh`), the newly issued token inherits the `family_id` of the token it replaces. This forms a lineage chain (Doc 02 Glossary — Token Family).

Logout and credential-change flows reuse the same revocation primitive:

| Trigger | Effect on refresh tokens |
|---------|--------------------------|
| `POST /auth/logout` | Revoke the current session's refresh token (`revoked_at = now()`). |
| `PATCH /users/me/password` (change) | Revoke **all** of the user's refresh tokens (all `family_id` values). |
| `POST /auth/reset-password` | Revoke **all** of the user's refresh tokens (all `family_id` values). |
| `DELETE /users/me` (account deletion) | Revoke + delete all tokens (cascade per 09 §8.2). |
| Reuse detected (§2.5) | Revoke all tokens sharing the same `family_id` (family-scoped — not user-wide). |

### 2.6 Expiry handling sequence (transparent client refresh — FE-2)

```
Access Token valid (≤15 min)        → request succeeds (200)
Access Token expired                → service returns 401
  → shared Axios client (single instance, FE-1) intercepts the 401
  → calls POST /auth/refresh with the refresh token   (rotation per §2.4)
  → on success: stores new pair, transparently RETRIES the original request
  → on 401 from refresh (expired/revoked/reused): clears tokens, routes user to login
```

This is invisible to calling components (FE-2 / REQ-FE-002). Concurrent 401s MUST funnel through a single in-flight refresh (a refresh mutex/queue) so the rotating token is not redeemed twice in parallel (which §2.5 would otherwise flag as reuse).

### 2.7 Token handling on the client (recommendation)

- Preferred: store the refresh token in a **`HttpOnly`, `Secure`, `SameSite=Strict` cookie**; keep the access token in memory only. This removes the refresh token from JavaScript reach (XSS mitigation).
- If tokens must be returned in the body (per `AuthTokenResponse`), the access token lives in memory (not `localStorage`); never log either token.
- CSRF: with cookie-borne refresh tokens, `/auth/refresh` MUST be protected against CSRF (SameSite + origin check). Bearer-header access tokens are not CSRF-susceptible.

### 2.8 Token persistence model (from 09 §2.3)

`refresh_tokens` stores `token_hash` (unique), `expires_at` (7-day), `revoked_at` (nullable), `user_id`. Indexes `idx_refresh_tokens_expires_at` support a scheduled cleanup of expired/revoked rows. **No plaintext token is ever stored.**

---

## 3. Authorization & Resource Ownership (P4 / SEC-3 / REQ-SEC-003)

### 3.1 The absolute rule

> **A General User MUST NEVER read or mutate another user's data.** Every endpoint that touches a
> user-owned resource MUST verify the resource's owner equals the caller. A mismatch returns
> **`403 Forbidden` — NEVER `404 Not Found`** (SEC-3). This is mandated by the Constitution and is
> intentional and consistent across the whole API (07 §1.1, §7.1).

### 3.2 How identity is established

1. The gateway/filter validates the Access Token (§2.2) and extracts `sub` → `callerUserId`.
2. `callerUserId` is placed into the request `SecurityContext` (the principal) and into **MDC** as part of trace context (CQ-12), but **never** logged as PII (it is a UUID, not email/name).
3. Services are stateless — `callerUserId` is re-derived from the token on **every** request (AL-5); there is no server session to trust.

### 3.3 The enforcement pattern (Controller → Service → Repository)

Ownership is enforced in the **Service layer** (business logic; CQ-1), not the controller and not by trusting client input.

```
Controller:  extracts callerUserId from SecurityContext, passes it into the Service call.
Service:     1. Load the resource by id (Repository returns Optional<T>, CQ-2).
             2. If empty → 404 Not Found        (resource genuinely does not exist for anyone).
             3. If resource.userId != callerUserId → 403 Forbidden   (exists, but not yours).
             4. Otherwise proceed.
Repository:  data access only; queries are scoped by user_id where listing (CQ-1, DB-6).
```

Worked decision table for `GET/PUT/DELETE /api/v1/expenses/{id}`:

| Situation | Status |
|-----------|--------|
| Token missing/expired/invalid | `401` |
| `{id}` does not exist at all | `404` |
| `{id}` exists and belongs to caller | `200`/`204` |
| `{id}` exists but belongs to **another user** | **`403`** (never 404) |
| Foreign **category** referenced in create/update (cross-service) | `403` (07 §4.1) |

> **403 vs 404 rationale.** A common alternative returns `404` for foreign resources to avoid leaking
> existence. The Constitution (SEC-3) **explicitly mandates `403, never 404`** for ownership
> violations, and this specification follows the Constitution. The deliberate trade-off: an
> unambiguous "forbidden" signal and uniform behaviour over existence-hiding. The mitigation against
> id-enumeration is that ids are **opaque, unguessable UUIDs** (09 DB-8).

### 3.4 List endpoints — no foreign data ever returned

List queries (`GET /expenses`, `/categories`, `/savings-goals`, `/budgets`, `/tags`, `/recurring-expenses`) MUST filter by `user_id = callerUserId` **in the repository query itself** (DB-6; `idx_*_user_id` exists on every table — 09 §9.2). A list MUST NOT return another user's rows under any filter combination, and the pagination envelope counts (`totalElements`) reflect only the caller's data.

### 3.5 Ownership matrix

| Resource (service) | Owner column | Read foreign → | Write foreign → | Notes |
|--------------------|--------------|----------------|-----------------|-------|
| Expense, Receipt, Tag, Recurring Expense (`expense-service`) | `user_id` | `403` | `403` | Receipt has denormalised `user_id` for fast checks (09 §4.2). |
| Custom Category (`category-service`) | `user_id` | `403` | `403` | — |
| **Default Category** (`category-service`) | `user_id = NULL` | **allowed (shared read)** | `403`/`409` | System categories are readable by all, **never editable/deletable** (07 §3, deletable=false). |
| Savings Goal, Contribution Entry (`savings-goal-service`) | `user_id` | `403` | `403` | — |
| Budget, Budget Period Ledger (`budget-service`) | `user_id` | `403` | `403` | — |
| User profile (`user-service`) | self (`/users/me`) | n/a | n/a | All access is to the caller's own `me` resource. |

### 3.6 Cross-service ownership

Cross-context references (`category_id`, `savings_goal_id`, `expense_id`) are bare UUIDs with **no DB FK** (09 §8.1). When one service references another's resource (e.g. an Expense citing a `categoryId`), it MUST validate **both existence and ownership/visibility** through the owning service's port/contract (AL-2). A foreign or invisible category on `POST/PUT /expenses` → **`403`** (07 §4.1). Services never read another service's database to perform this check (AL-1).

---

## 4. Data Privacy — PII Classification & Log Masking (CQ-13 / REQ-OBS-004)

### 4.1 Data classification

Every persisted field is classified. The handling rule is driven by the class.

| Class | Definition | Logging rule | Examples |
|-------|------------|--------------|----------|
| **SECRET** | Credentials / tokens / hashes | **NEVER logged or returned**, in any form | password (plaintext), `password_hash`, refresh/reset/verification token & `token_hash`, `JWT_SECRET`, DB/MinIO/SMTP credentials |
| **PII** | Identifies a natural person | **NEVER logged in clear**; masked if unavoidable; not in error `message` | `full_name`, `email` |
| **SENSITIVE-FINANCIAL** | Reveals personal finances | **NEVER logged** (CQ-13 names amounts explicitly); not in error `message` | `amount`/`currency`, `merchant`, `description`, `notes`, budget limits, goal targets, contribution amounts, receipt image content/EXIF |
| **INTERNAL-ID** | Opaque identifiers, safe to log | Allowed (preferred over PII for correlation) | `user_id`, `expense_id`, `jti`, `traceId` |
| **PUBLIC/META** | Non-identifying metadata | Allowed | timestamps, HTTP method/path/status, enum values, page/size |

### 4.2 PII / sensitive field inventory (by database — from 09)

| Database.table | Field(s) | Class |
|----------------|----------|-------|
| `identity_db.users` | `full_name`, `email` | **PII** |
| `identity_db.users` | `password_hash` | **SECRET** |
| `identity_db.refresh_tokens` / `email_verifications` / `password_reset_tokens` | `token_hash` | **SECRET** |
| `identity_db.data_exports` | `download_ref` | SENSITIVE (links to a full personal data dump) |
| `expense_db.expenses` | `amount`/`currency`, `merchant`, `description`, `notes` | **SENSITIVE-FINANCIAL** |
| `expense_db.receipts` | image bytes (in MinIO), `storage_ref`, EXIF metadata | **SENSITIVE-FINANCIAL** (EXIF may carry GPS/PII) |
| `savings_goal_db.savings_goals` | `name`, `description`, `target_amount` | **SENSITIVE-FINANCIAL** |
| `savings_goal_db.contribution_entries` | `amount` | **SENSITIVE-FINANCIAL** |
| `budget_db.budgets` / `budget_period_ledgers` | `budget_limit`, `spent`, `carried_in` | **SENSITIVE-FINANCIAL** |
| all user-owned tables | `user_id` | INTERNAL-ID (safe) |

### 4.3 Log masking rules (applied at the logging boundary)

Logs use structured fields + `traceId` (CQ-11/CQ-12). Correlate by **`user_id` (UUID)**, never by email/name.

| Field | In logs |
|-------|---------|
| `email` | Masked: keep first char of local part + first char of domain → `j****@e****.com`. Full address never logged. |
| `full_name` | Masked: first initial only → `S****`. Not logged unless essential; prefer omitting. |
| `password` (plaintext) | **Never** present in any log line, exception, or request dump. |
| `password_hash`, any `token`/`token_hash`, secrets | **Fully redacted** → `***REDACTED***`; never partially shown. |
| `amount`, `budget_limit`, `target_amount`, `spent` | **Never logged** (CQ-13 explicitly forbids amounts). Log the operation + `expenseId`, not the value. |
| `merchant`, `description`, `notes` | Not logged (free text may contain PII). |
| Authorization header / Bearer token / refresh token | Fully redacted in request/response logging filters. |
| `user_id`, `traceId`, `jti`, HTTP method/path/status/latency | Logged (these power observability without exposing PII). |

Implementation requirements:
- A central **masking utility** (e.g. `PiiMasker` / log marker + serializer) is the single sanctioned way PII reaches a log; ad-hoc `log.info("...", user.getEmail())` is a defect.
- The uniform **error envelope** `message` MUST NOT contain PII or amounts (07 §1.4). Validation errors reference the **field name and rule**, not the offending value (e.g. `"amount must be greater than 0"`, never the value).
- DTO/`toString()` on entities holding PII must not dump raw PII into logs/stack traces.
- A **PII leak in logs is treated as a security incident** and blocks release (CQ-13).

### 4.4 Privacy rights — export & erasure

| Right | Mechanism | Notes |
|-------|-----------|-------|
| **Data export / portability** | `POST /users/me/data-export` → async assembly, `data_exports.download_ref` to a time-limited object-store link | The export bundles the user's own data only; the link is SENSITIVE (treat like a credential, expire it). |
| **Right to erasure** | `DELETE /users/me` → removes the user and **all** their data | Cascades delete tokens/verifications (09 §8.2); each service purges rows where `user_id = caller` on the deletion event; receipts removed from MinIO. |
| **Token/credential cleanup** | Expired/revoked tokens pruned via `expires_at` index | Reduces standing secret material. |

### 4.5 Data minimisation

- JWT carries only `sub` (UUID) + standard claims — no email/name in the token.
- Cross-service messages/DTOs carry **ids**, not PII (AL-4 / 09 §8.1).
- Receipt EXIF (which can contain GPS coordinates and device PII) MUST be stripped on upload (§5.3).

---

## 5. Input Validation & File Security (API-7 / SEC-5 / REQ-API-007 / REQ-SEC-005)

### 5.1 Server-side validation is mandatory and authoritative

All input is validated **server-side regardless of client-side validation** (API-7). Client validation (FE-5) is UX only and is never trusted. Validation failures return **`400 Bad Request`** with the uniform error envelope and optional field-level `errors[]` (07 §1.4).

General rules applied to every request DTO:
- **Type & enum**: enum fields must match the exact `UPPER_SNAKE_CASE` value set (e.g. `paymentMethod ∈ {UPI,CASH,CREDIT_CARD,DEBIT_CARD,NET_BANKING,OTHER}`); unknown value → 400.
- **Bounds**: string lengths must not exceed the DB column limits (09) — e.g. `full_name ≤ 150`, `email ≤ 255`, `description ≤ 500`, `notes ≤ 1000`, `merchant ≤ 150`, `tag.name ≤ 50`, `category.name ≤ 80`, `goal.name ≤ 120`.
- **Money**: `amount` is a decimal string scale-2 (07 §1.5), parsed to `NUMERIC(19,4)`, must be `> 0` where required (`ck_*_amount_positive`); currency must be `INR`.
- **Dates**: ISO-8601, validated and interpreted in the user's timezone.
- **Required fields**: enforced per DTO (07 §2–§6).
- **Injection defence**: all persistence goes through parameterised JPA/queries (no string-concatenated SQL); output is encoded by the React client to prevent stored XSS from free-text fields (`description`, `notes`, `merchant`).
- **Mass-assignment**: only DTO-declared fields are bound; `user_id`/ownership is **never** taken from the request body — always from the token (§3.2).

### 5.2 Receipt upload — server-side constraints (SEC-5 / REQ-SEC-005)

Endpoint: `POST /api/v1/expenses/{id}/receipt` (multipart/form-data, `ReceiptUpload`). Constraints, all enforced server-side:

| Constraint | Rule | On violation |
|------------|------|--------------|
| **Max size** | **≤ 5 MB (5 242 880 bytes)** | `400 Bad Request` |
| **Allowed types** | **JPEG, PNG, WEBP only** (`image/jpeg`, `image/png`, `image/webp`) | `400 Bad Request` |
| **Type detection** | Determined by **magic-byte / content sniffing**, NOT by file extension or the client-supplied `Content-Type` alone | `400` if sniffed type ∉ allowlist |
| **Declared vs actual** | Client `Content-Type` MUST match the sniffed content type | `400` on mismatch |
| **One per expense** | At most one receipt per Expense (`uq_receipts_expense_id`, EXP-INV-7); upload replaces | replace existing |
| **Ownership** | The `{id}` Expense must belong to the caller (§3) | `403` (foreign) / `404` (absent) |

Defence-in-depth — the 5 MB limit is enforced at **multiple layers** so a large body is rejected early:
1. **Gateway / reverse proxy** request body cap.
2. **Framework multipart config** (`max-file-size` / `max-request-size`) → rejects before the handler buffers it.
3. **Service-layer check** on the actual byte count before persisting.
4. **Database** `ck_receipts_size_max CHECK (size_bytes <= 5242880)` and `mime_type` CHECK as the final backstop (09 §4.2).

### 5.3 Receipt content & storage safety

- **No DB storage of binaries.** The image is stored in **object storage (MinIO)**; PostgreSQL holds only `storage_ref`, `mime_type`, `size_bytes` (09 §4.2, Assumption 5).
- **Generated storage key.** The object key is a server-generated UUID/path — the **original client filename is never used** as a filesystem/object path (prevents path traversal and overwrite attacks). Original filename, if retained, is sanitised and stored as metadata only.
- **Re-encode / strip metadata.** On accept, the image **MUST** be re-encoded to a canonical form and **EXIF metadata stripped** (removes GPS/device PII — §4.5 / Doc 02 Glossary: EXIF — mandatory privacy control, not optional hardening) and neutralises polyglot/embedded payloads. Failing to strip EXIF before the MinIO write is a release blocker (CON-001 — aligned with Doc 02 Glossary MUST mandate).
- **Safe serving.** `GET /expenses/{id}/receipt` serves with the correct `Content-Type` and `Content-Disposition` (e.g. `inline`/`attachment` with a safe name), plus `X-Content-Type-Options: nosniff` so the browser cannot interpret an image as HTML/script. Access is ownership-checked (§3).
- **No active content.** SVG and HTML are **not** in the allowlist (script-bearing formats are excluded by design). Optional: server-side malware/AV scan before the object is made retrievable.
- **Decompression / pixel-flood guard**: enforce sane max dimensions on decode to avoid decompression-bomb DoS.

### 5.4 Authentication rate limiting (SEC-4 / REQ-SEC-004)

Public auth endpoints MUST be rate-limited to resist brute force and enumeration:

| Endpoint | Limit (guidance) | Response on breach |
|----------|------------------|--------------------|
| `POST /auth/login` | Per-IP **and** per-account throttle (e.g. progressive backoff after ~5 failures) | `429 Too Many Requests` |
| `POST /auth/register` | Per-IP throttle | `429` |
| `POST /auth/forgot-password` | Per-IP + per-email throttle; uniform `202` regardless of account existence (no enumeration) | `429` |

`429` uses the uniform error envelope and **MUST include a `Retry-After: <seconds>` response header** (an integer count of seconds the client must wait before retrying). Clients MUST honour `Retry-After` and not retry within the backoff window — repeated requests inside the window extend the lockout. Failed logins are logged at `WARN` with `traceId` + masked email (§4.3), never the password.

### 5.5 CSV Import — server-side constraints (S-03 / REQ-EXP-012/013)

Endpoint: `POST /api/v1/expenses/import` (multipart/form-data). All constraints are enforced server-side:

| Constraint | Rule | On violation |
|------------|------|--------------|
| **Max file size** | **≤ 10 MB** | `400 Bad Request` |
| **Content type** | `text/csv` only; validated server-side — client `Content-Type` header is not trusted | `400 Bad Request` |
| **Max rows** | **≤ 10 000 rows** per import to bound memory and processing time (CQ-10 / REQ-DB-003) | `400 Bad Request` |
| **CSV injection** | Strip leading formula-trigger characters (`=`, `+`, `-`, `@`, `\t`, `\r`) from **every cell value** before parsing; prevents formula execution when the export is opened in spreadsheet software | Sanitised silently (no rejection) |
| **Ownership / mass-assignment** | Caller's `userId` (from the Access Token) is applied to every imported Expense row; any `user_id` column present in the CSV is **silently ignored** (§3.2) | Ignored |
| **Goal association** | If a goal-name column is present, match against the caller's own Savings Goals only; cross-user goal association is impossible | Warning entry in `ImportExpensesReport.results[].warning` |

> **Note.** CSV import does not involve binary image data and therefore does not require magic-byte sniffing. The `text/csv` content check is a MIME assertion on the multipart file field, enforced after the framework buffers the upload — the 10 MB cap is applied at the multipart-config layer (the same defence-in-depth approach as §5.2 layer 2).

---

## 6. Secrets Management (SEC-6 / P5 / REQ-SEC-006)

| Secret | Source | Rule |
|--------|--------|------|
| `JWT_SECRET` | Environment variable / secret store | Signs/validates access tokens; never committed; rotation invalidates outstanding access tokens (≤15 min blast radius). |
| `DB_PASSWORD` (per service) | Env / secret store | Each service owns its own DB credentials (AL-1). |
| MinIO / object-store credentials | Env / secret store | — |
| SMTP password | Env / secret store | — |

Rules (P5):
- **No secret is ever hardcoded or committed.** Config references env vars only (FE-6 forbids hardcoded config on the client too; the SPA reads only public base URLs from env, never secrets).
- A committed secret triggers the incident path: **commit reverted, secret rotated, incident logged**.
- Secrets are excluded from logs (§4.3 SECRET class), error messages, and any DTO.

---

## 7. Threat Model Summary (controls ↔ threats)

| Threat | Primary control(s) | Section |
|--------|--------------------|---------|
| Credential stuffing / brute force | Rate limiting + BCrypt cost 12 + uniform errors | §1.1, §5.4 |
| Stolen access token | 15-min TTL; stateless signature validation | §2.1–§2.2 |
| Stolen / replayed refresh token | One-time rotation + reuse detection → family revocation | §2.4–§2.5 |
| Horizontal privilege escalation (User A → User B's data) | Service-layer ownership check, **403 never 404**, list scoping, opaque UUIDs | §3 |
| ID enumeration | Opaque UUID ids; uniform 403 | §3.3 |
| PII / financial data leak via logs | Classification + masking utility + no-PII error envelope | §4 |
| Malicious upload (oversize, wrong type, polyglot, path traversal, XSS via image) | Size/type allowlist, magic-byte sniffing, generated key, EXIF strip, `nosniff`, no active formats | §5.2–§5.3 |
| Injection (SQLi / XSS) | Parameterised queries, server-side validation, output encoding | §5.1 |
| Secret disclosure | Secrets externalised; hashed tokens at rest; incident path | §6 |
| Session fixation / CSRF (cookie mode) | Stateless JWT; SameSite + origin checks on cookie refresh | §2.7 |

---

## 8. Verification & Test Mandates

Per CQ-5/CQ-6/CQ-7, the following security behaviours MUST have automated tests (unit + Testcontainers integration):

1. **Auth lifecycle**: login issues a 15-min access token + 7-day refresh token; an expired access token → 401; refresh rotates (old refresh token → 401 on reuse); reuse of a revoked token revokes the whole family; logout/password-change revoke refresh tokens.
2. **Ownership (403-never-404)**: for every `/{id}` endpoint, User B accessing User A's resource → **403**; a non-existent id → 404; lists never return foreign rows.
3. **PII masking**: assert no test log line contains a raw email, name, amount, password, or token; error responses contain no PII/amounts.
4. **Upload validation**: reject > 5 MB; reject non-JPEG/PNG/WEBP (including a file with image extension but non-image bytes, and an HTML/SVG payload); accept valid image; confirm EXIF stripped and stored by generated key; foreign expense → 403.
5. **Secrets**: a CI/static check asserts no secret literals in the repo; app boots only with env-provided secrets.
6. **Rate limiting**: repeated login failures trigger `429`; `forgot-password` returns uniform `202` for known and unknown emails.

A failure of any §8 test, or any violation of §3 (ownership), §4 (PII), or §5/§6, is a **release blocker** (Constitution Governance — no override).

---

## 9. Assumptions

1. **403-never-404** is applied per Constitution SEC-3 even though existence-hiding (404) is a common alternative; opaque UUIDs mitigate enumeration. This is a deliberate, Constitution-bound choice.
2. **Access Token = JWT (HS256 with `JWT_SECRET`); Refresh Token = opaque random** stored only as a SHA-256 hash. RS256 with a managed key pair is an acceptable hardening upgrade and does not change the lifecycle.
3. **Refresh token transport** defaults to the request body per `AuthTokenResponse`/`RefreshTokenRequest` (07 §2.2); an `HttpOnly` cookie is the recommended hardening and, if adopted, adds the CSRF controls in §2.7.
4. **Receipt re-encoding / EXIF stripping and optional AV scanning** are specified as required privacy/safety controls; the engine (e.g. image library, ClamAV) is an implementation choice.
5. **Object storage is MinIO** (01-context / 09 Assumption 5); receipt binaries never reside in PostgreSQL.
6. **Reuse-detection family revocation** uses `family_id UUID` on `refresh_tokens` (Doc 09 §2.3 / N-02). Only the compromised session chain (same `family_id`) is revoked. Credential-change flows (password reset, account deletion) revoke all families for the user — see §2.5.
7. **Email verification link (S-07 — accepted risk).** `GET /auth/verify-email?token=<opaque-token>` exposes the one-time token in the URL, where it may appear in server access logs and browser history. This is accepted because: (a) the token is a high-entropy opaque value never derived from user PII; (b) tokens expire after 24 h; (c) tokens are single-use (`consumed_at` stamped on first use — Doc 09 §2.2); (d) TLS (§1.2) is mandatory end-to-end. Changing to a POST endpoint would break click-to-verify UX in all email clients. Mitigations in place make the residual risk low; the pattern is consistent with industry practice (OAuth2 authorization codes, SES verification links).

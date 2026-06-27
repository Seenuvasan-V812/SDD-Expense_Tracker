# Contract — `user-service` (Identity & Access)

**Base**: `/api/v1` | **Derived from**: [`07-api-specification.md`](../07-api-specification.md) §2 + Doc 10 §2.
**OpenAPI is the single source of truth (P1/API-6); this is its human-readable design.**

## Global rules (apply to every endpoint)
- **Auth**: `Authorization: Bearer <access JWT>` except the public auth endpoints below. Identity from token only (AL-5); never from body.
- **Pagination envelope** (API-2) on lists: `content, page, size, totalElements, totalPages` (`page` 0-based, `size` default 20 max 100).
- **Error envelope** (API-3): `timestamp, status, error, message, path, traceId` — no PII/amounts in `message` (CQ-13).
- **Status codes** (API-4): 200 / 201+`Location` / 204 / 400 / 401 / 403 (ownership, **never 404**) / 404 / 409 / 429+`Retry-After` / 500.
- **Security response headers** (S-08, global filter): `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, restrictive `Content-Security-Policy`.

## Endpoints (13)

| Method | Path | Purpose | Success | Key failures | Auth | Req |
|--------|------|---------|---------|--------------|------|-----|
| POST | `/auth/register` | Register General User (inactive until verified) | 201 + `Location` | 400, 409 dup email | public (rate-limited) | REQ-USR-003/004 |
| GET | `/auth/verify-email?token=` | Activate via Email Verification link (single-use) | 200 | 400 bad/expired | public | REQ-USR-004 |
| POST | `/auth/login` | Authenticate; issue access + refresh | 200 | 400, 401 bad creds/unverified, 429 | public (rate-limited) | REQ-USR-005, SEC-2/4 |
| POST | `/auth/refresh` | Rotate refresh; issue new pair (reuse ⇒ family revoke) | 200 | 401 invalid/reused | public | REQ-SEC-002 |
| POST | `/auth/logout` | Revoke this session's refresh token (`LogoutRequest`) | 204 | 400 missing body, 401 | Bearer | REQ-USR-006 |
| POST | `/auth/forgot-password` | Send time-limited reset link (no enumeration) | 202 | 400, 429 | public (rate-limited) | REQ-USR-007 |
| POST | `/auth/reset-password` | Reset via token; **revoke all refresh tokens** | 200 | 400 bad/expired | public | REQ-USR-007 |
| PATCH | `/users/me/password` | Change password (current required); **revoke all refresh tokens** | 204 | 400, 401 | Bearer | REQ-USR-009 |
| GET | `/users/me` | Get own profile | 200 | 401 | Bearer | REQ-USR-008 |
| PUT | `/users/me` | Update profile (name, currency, timezone, locale, weeklyDigest) | 200 | 400, 401 | Bearer | REQ-USR-008, REQ-NOTIF-001 |
| DELETE | `/users/me` | Delete account (removes all data; broadcast `UserDeletedEvent`) | 204 | 401 | Bearer | REQ-USR-010 |
| POST | `/users/me/data-export` | Request full Data Export (async) | 202 | 401 | Bearer | REQ-USR-011 |
| GET | `/users/me/data-export/{exportId}/download` | Signed download URL for ready export | 200 | 401, 404 unknown, 409 not ready | Bearer | REQ-USR-011 |

## DTOs (pseudo-OpenAPI)
- `RegisterUserRequest{fullName, email, password}` — password BCrypt ≥12, never returned.
- `LoginRequest{email, password}`.
- `AuthTokenResponse{accessToken (15-min JWT), refreshToken (7-day, rotates), tokenType:"Bearer", expiresInSec:900}` — FE MUST NOT store refresh in local/sessionStorage; prefer HttpOnly cookie.
- `RefreshTokenRequest{refreshToken}`; `LogoutRequest{refreshToken}`.
- `UpdateProfileRequest{fullName, preferredCurrency, timezone, locale?, weeklyDigestEnabled?}`.
- `ChangePasswordRequest{currentPassword, newPassword}`.
- `UserProfileResponse{userId, fullName, email, preferredCurrency, timezone, locale, weeklyDigestEnabled, status∈{INACTIVE_UNVERIFIED,ACTIVE}, createdAt}` — **no password/passwordHash field, ever**.

## Security notes
- JWT HS256, claims `sub`(userId UUID)/`iat`/`exp`/`jti`/`typ:"access"`; no PII in claims. 401 on signature/expiry/typ failure.
- Refresh token opaque, SHA-256-hashed at rest; one-time rotation; reuse of revoked token ⇒ revoke whole `family_id`.
- Rate limit login/register/forgot-password → 429 + `Retry-After`; uniform errors (no "user exists" leak).

## Events emitted to outbox (Phase 1: relay→Kafka, no consumer)
`UserRegisteredEvent{userId, deliveryRef}`, `UserVerifiedEvent`, `PasswordResetRequestedEvent{userId, deliveryRef, expiresAt}`,
`UserDeletedEvent{userId, deletedAt}` (consumed by all owning services for cascade), `DataExportRequestedEvent`/`DataExportReadyEvent`.
Raw verification/reset tokens never leave the service (resolved later via `SecureNotificationDeliveryPort`).

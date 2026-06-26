# Security & Privacy Specification Quality Checklist: Daily Expense Application

**Purpose**: Validate the Security & Privacy Specification for completeness, fidelity, and constitutional compliance before proceeding to planning
**Created**: 2026-06-25
**Document**: [10-security-specification.md](../10-security-specification.md)

## Content Quality

- [x] No invented controls — every control traces to a Constitution law (§8 SEC-1…6, P4, P5, CQ-13) and a catalogued requirement (REQ-SEC-*, REQ-API-007, REQ-OBS-004)
- [x] Focused on exact, testable security/privacy behaviour (not generic advice)
- [x] Written with clear heading hierarchy for AppSec, backend, and frontend stakeholders
- [x] All four mandated areas completed (Auth token lifecycle, Authorization/ownership, PII/log masking, Input & file validation)

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirement Map (§0) links every control to Constitution + requirement IDs
- [x] **Authentication**: 15-min JWT access token + 7-day rotating refresh token lifecycle fully detailed (login, refresh+rotation, reuse detection, revocation triggers, transparent client refresh)
- [x] **Authorization**: 403-never-404 ownership rule defined with Controller→Service→Repository pattern, decision table, ownership matrix, and cross-service handling
- [x] **Data Privacy**: PII/sensitive fields inventoried per database (07/09), classification scheme, and exact per-field log-masking rules defined
- [x] **Input & File Security**: server-side validation rules + 5 MB / JPEG-PNG-WEBP receipt constraints with magic-byte sniffing, multi-layer size enforcement, generated key, EXIF strip, safe serving
- [x] Rate limiting (SEC-4) and secrets management (SEC-6/P5) covered
- [x] Controls are testable and unambiguous (each has a defined trigger and HTTP/behaviour outcome)
- [x] Scope is clearly bounded (five core services; Notification/Income/Reporting deferred consistent with 07/09)
- [x] Dependencies and assumptions identified (§9)

## Feature Readiness

- [x] Aligns with API contract (07): status codes, error envelope, auth endpoints, ownership column
- [x] Aligns with data contract (09): `refresh_tokens` rotation columns, `receipts` mime/size CHECKs, `user_id` ownership column, no cross-service FKs
- [x] No contradiction with the Constitution; 403-never-404 trade-off explicitly justified per SEC-3
- [x] Verification & test mandates (§8) map every control to required unit/Testcontainers tests
- [x] Release-blocker conditions (P4/§8/§4/§5/§6) stated per Governance

## Notes

- **403 vs 404**: The Constitution (SEC-3) mandates `403, never 404` for ownership violations; the spec follows the Constitution and documents the existence-hiding trade-off plus the opaque-UUID mitigation (§3.3, Assumption 1).
- **Token design**: Access = JWT (HS256/`JWT_SECRET`), Refresh = opaque random stored as SHA-256 hash with rotation + reuse detection; RS256 noted as an acceptable hardening (§2, Assumption 2).
- **Receipt safety** extends beyond SEC-5's type/size minimum to add magic-byte sniffing, EXIF stripping, generated storage keys, and `nosniff` serving as defence-in-depth (§5.3) — additive, no requirement contradicted.
- All checklist items pass; document is ready for `/speckit-plan`.

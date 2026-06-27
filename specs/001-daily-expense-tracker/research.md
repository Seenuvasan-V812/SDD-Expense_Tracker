# Phase 0 Research — Daily Expense Application (Phase 1)

**Input**: `plan.md` Technical Context | **Date**: 2026-06-27

> **Scope of this document.** The technology stack and all architectural decisions are **already fixed**
> by the Engineering Constitution v1.1.2 and spec.md Section 4 — they are not re-opened here (Constitution
> §4 requires an amendment to change the stack). Per the `/speckit-plan` mandate, `research.md` records
> only genuine **NEEDS CLARIFICATION** items not resolved anywhere in the 15 authoritative sources, and
> cites which source settles each resolved decision.

## 1. NEEDS CLARIFICATION — open items

**None.** Every Technical Context field is resolved by an authoritative source. No `NEEDS CLARIFICATION`
markers remain in `plan.md`. The Constitution Check gate passes with no unjustified violations.

## 2. Decisions already settled by the sources (for traceability)

These are **not** open questions; they are recorded so downstream work cites the settling source rather than
re-deriving. "Alternatives considered" is "none — fixed by governance" unless noted.

| Decision | Resolution | Settled by |
|----------|------------|-----------|
| Backend language/framework | Java 21 + Spring Boot 3.x, one service per bounded context | Constitution §4; spec §4 |
| Persistence | PostgreSQL, one DB per service, Flyway migrations, no cross-schema joins/FKs | Constitution §4, AL-1; Doc 09 DB-1/DB-2 |
| Money representation | `NUMERIC(19,4)` + `currency='INR'`; `BigDecimal`/`Money` in code, never `double` | Doc 09 DB-5; Doc 07 §1.5; INV-6 |
| Auth tokens | JWT HS256 15-min access; 7-day rotating refresh stored as SHA-256 hash; BCrypt ≥ 12 | Constitution SEC-1/SEC-2; Doc 10 §2 |
| Refresh-token theft handling | One-time rotation + reuse detection → revoke entire **Token Family** (`family_id`) | Doc 10 §2.5; Doc 09 §2.3 |
| Ownership semantics | Service-layer check → **403, never 404**; opaque UUID ids; list scoped by `user_id` | Constitution SEC-3/P4; Doc 10 §3 |
| Cross-context integration | Anti-Corruption Ports (8) + async domain events; refs are bare UUIDs validated via port | Doc 05 §8; Doc 08; AL-1/AL-2 |
| Event reliability | Per-service **transactional outbox** → relay → Kafka; at-least-once; idempotent on `eventId` | Constitution §4; Doc 08 §1.2/§1.3; Doc 09 §7 |
| Object storage | MinIO (S3-compatible); receipts/exports never on service disk; signed URLs | Doc 01 §4.1; Doc 02 (Object Storage); SEC-5 |
| Receipt safety | magic-byte sniff (not client `Content-Type`), JPEG/PNG/WEBP ≤ 5 MB, **EXIF strip**, ≤ 25 MP pixel guard, server-generated UUID key, `nosniff` on serve | Doc 10 §5.2/§5.3; Doc 07 §4.4 |
| CSV import/export bounds | import ≤ 10 MB / ≤ 10 000 rows, `text/csv` server-validated, formula-injection strip; export streamed | Doc 10 §5.5; Doc 07 §4.4; CQ-10 |
| Frontend | React 18 + TS strict (no `any`), Vite, single shared Axios + transparent single-flight refresh, env-only config | Constitution FE-1..FE-6; spec §4 |
| Testing strategy | Mockito unit per service class; Testcontainers integration per endpoint group (happy + 400/401/403/404/409); Kafka Testcontainers for event flow | Constitution CQ-5/6/7; Doc 11 §3; Doc 14 |
| Build/phase order | shared-kernel → user → category+expense → savings+budget → outbox infra → frontend | Doc 12 §2 |
| Idempotency-Key on retry-sensitive POSTs (contributions, CSV import, recurring generation) | Honored; backed by persisted once-per-period / unique guards; OpenAPI declares the header | Doc 11 Appendix B; Doc 02 (Idempotency-Key) |

## 3. Phase-deferral confirmations (spec.md §7)

Recorded so no research effort or milestone leaks deferred scope into Phase 1:

- `income-service` (REQ-INC-001..004), `income_db` — **deferred (O-04)**; no API/schema/research in Phase 1.
- `reporting-service` / Dashboard / Reports (REQ-DASH, REQ-RPT, REQ-GOAL-014) — **deferred (O-01/O-02/O-07)**.
- `notification-service` **consumer**, Notification Center (REQ-NOTIF-004/005), in-app delivery — **deferred (O-03)**.
- Active services **do** write notification/reporting events to the outbox in Phase 1; the relay publishes
  them to Kafka per topic retention (default 7 days) — but **no consumer** exists until Phase 2 (Doc 08 §1.2.1).

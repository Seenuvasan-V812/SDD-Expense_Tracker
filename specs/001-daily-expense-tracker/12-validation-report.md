
---

---

# Second-Pass Validation Report — 2026-06-26

**Scope:** Re-audit after remediation of all First-Pass findings. Cross-references updated Docs 02, 03, 05, 06, 07, 08, 10 against Docs 01, 04, 09, 11 (unchanged).

---

## Resolution Status (First-Pass Findings)

| ID | Finding | Status | Notes |
|---|---|---|---|
| M-01 | `PATCH /savings-goals/{id}/status` state machine contradiction | ✅ **RESOLVED** | Docs 06 §3.3/3.4 + Doc 07 §5.1/5.2 aligned; COMPLETED/ABANDONED transitions blocked |
| M-02 | `Tag` VO/Entity contradiction | ⚠️ **PARTIALLY RESOLVED** | Doc 05 promoted Tag to `«E»`; Doc 06 §2.1 `tags` type still `Set<Tag>` — see N-03 |
| M-03 | `RecurringGenerationFailedEvent` attributed to `expense-service` for Income | ❌ **OPEN** | No fix applied; Income context still entirely deferred |
| M-04 | `UpdateSavingsGoalRequest` DTO missing | ✅ **RESOLVED** | Added to Doc 07 §5.2 |
| M-05 | `UpdateBudgetRequest` DTO missing | ✅ **RESOLVED** | Added to Doc 07 §6.2 |
| M-06 | `POST /auth/logout` no request body | ✅ **RESOLVED** | `LogoutRequest: { refreshToken }` added to Doc 07 §2.1/2.2 |
| S-01 | Raw secret tokens in event payloads | ✅ **RESOLVED** | `deliveryRef` pattern in Doc 08 §3.1/3.3; `SecureNotificationDeliveryPort` in Doc 05 §8 |
| S-02 | `downloadRef` in `DataExportReadyEvent` | ✅ **RESOLVED** | Removed from Doc 08 §3.5; authenticated `GET .../download` endpoint added to Doc 07 |
| S-03 | CSV bulk import has zero security constraints | ✅ **RESOLVED** | Full CSV constraints + column schema in Doc 07 §4.4; Doc 10 §5.5 |
| S-04 | Receipt GET response security headers missing | ✅ **RESOLVED** | `Content-Disposition`, `X-Content-Type-Options: nosniff` documented in Doc 07 §4.4 |
| S-05 | Magic-byte content sniffing absent from API contract | ✅ **RESOLVED** | Explicit note in Doc 07 §4.4 `ReceiptUpload` |
| S-06 | `429 Too Many Requests` + `Retry-After` missing | ✅ **RESOLVED** | Added to Doc 07 §1.1 and Doc 10 §5.4 |
| S-07 | Email verification token in GET query parameter | ❌ **OPEN** | Low risk; acknowledged in Doc 10 §2.2 as accepted pattern |
| S-08 | Security response headers not referenced in API contract | ❌ **OPEN** | Doc 10 §1.2 mandates them; Doc 07 has no cross-reference |
| O-01 | Dashboard API zero endpoints (REQ-DASH-001…006) | 📋 **DEFERRED — Phase 2** | Doc 03 §2.9 deferral banner + Note 7 |
| O-02 | Reports API zero endpoints (REQ-RPT-001…005) | 📋 **DEFERRED — Phase 2** | Doc 03 §2.10 deferral banner + Note 7 |
| O-03 | Notification Center API missing (REQ-NOTIF-004/005) | 📋 **DEFERRED — Phase 2** | Doc 03 §2.11 inline markers |
| O-04 | Income API missing (REQ-INC-001…004) | 📋 **DEFERRED — Phase 2** | Doc 03 §2.6 deferral banner + Note 7 |
| O-05 | Weekly Digest opt-in toggle missing from API | ✅ **RESOLVED** | `weeklyDigestEnabled` in Doc 07 §2.2 — NEW GAP N-01 (DB schema) |
| O-06 | `locale` not updatable via API | ✅ **RESOLVED** | `locale` in Doc 07 §2.2 `UpdateProfileRequest` + `UserProfileResponse` |
| O-07 | REQ-GOAL-014 Dashboard goals section no provider | 📋 **DEFERRED — Phase 2** | Doc 03 §2.5 inline marker |
| O-08 | CSV import format not specified | ✅ **RESOLVED** | Full 10-column schema in Doc 07 §4.4 `CsvImport` |
| G-01…G-10 | 10 Glossary Gaps | ✅ **RESOLVED** | All 10 terms added to Doc 02 |

---

## Second-Pass New Findings

### N-01 — HIGH: `weekly_digest_enabled` column missing from Doc 09 `users` table

**Where:** Doc 07 §2.2 — `UpdateProfileRequest.weeklyDigestEnabled: boolean?` and `UserProfileResponse.weeklyDigestEnabled: boolean` (added to fix O-05). Doc 09 §2.1 — `users` table has no `weekly_digest_enabled` column.

**Problem:** The `weeklyDigestEnabled` preference is now part of the API contract and the domain VO (`UserProfile`, Doc 05 §3.1) but has no backing database column. Without it, the preference cannot be persisted; `GET /users/me` cannot return it, and `PUT /users/me` cannot update it. This is a blocking data contract gap introduced by the O-05 fix.

**Fix required:** Add `weekly_digest_enabled BOOLEAN NOT NULL DEFAULT false` to Doc 09 §2.1 `users` table, with an index if frequently queried (e.g., `idx_users_weekly_digest` for the scheduler's opt-in fan-out — but since the scheduler will likely filter on this column to determine who to include in the digest, an index is warranted).

---

### N-02 — MEDIUM: `family_id` column missing from Doc 09 `refresh_tokens` for Token Family revocation

**Where:** Doc 10 §2.5 — "the entire Token Family is immediately revoked" on reuse detection. Doc 02 (Glossary) — Token Family: "Token Family ID is stored alongside each refresh token hash." Doc 09 §2.3 — `refresh_tokens` table: no `family_id` column.

**Problem:** The specs (both Doc 10 and the new Glossary entry) assert that Token Family tracking is DB-backed, but the `refresh_tokens` schema has no `family_id` or `family_ref` column. There are two implementation paths:

| Approach | What it means |
|---|---|
| **Family-scoped revocation** (spec intent) | Add `family_id UUID NOT NULL` to `refresh_tokens`; revoke only tokens sharing the same `family_id` on reuse detection. Proportionate response — only the compromised session chain is killed. |
| **User-wide revocation** (simpler, more conservative) | On reuse detection, revoke ALL refresh tokens for the `user_id`. No schema change required, but terminates all the user's concurrent sessions. |

The current specs mandate family-scoped revocation but the schema does not support it. Either the Glossary/Doc 10 wording must be relaxed to "all tokens for the user are revoked," or `family_id` must be added to Doc 09.

**Fix required:** Either (A) add `family_id UUID NOT NULL` to `refresh_tokens` and document the family-assignment rule (new token inherits parent's `family_id`; first-ever token for a user generates a new UUID), or (B) update Doc 10 §2.5 and the Glossary Token Family definition to explicitly state "all refresh tokens for the user are revoked on reuse detection" and remove the `family_id` storage claim.

---

### N-03 — LOW: Doc 06 §2.1 `tags` property type not updated to `Set<TagId>` after M-02 fix

**Where:** Doc 06 §2.1 `Expense` state table — `tags | Set<Tag> | no | Cross-Category labels; may be empty, never null.` Doc 05 §5.1 (fixed for M-02) — `Set<«VO» TagId>` (cross-aggregate Tag refs by Id; Tag is a «E» with its own lifecycle).

**Problem:** When Tag was promoted from `«VO»` to `«E»` in Doc 05, the corresponding update was not applied to Doc 06 §2.1. The type `Set<Tag>` implies embedding the full Tag Value Object inside Expense, contradicting the Entity promotion. A Java developer following Doc 06 would hold `Set<Tag>` (object references) instead of `Set<TagId>` (cross-aggregate ID references), violating AL-1.

**Fix required:** Change `| 'tags' | Set<Tag> |` to `| 'tags' | Set<TagId> |` in Doc 06 §2.1. The notes column should be updated to: "Cross-aggregate Tag references by `TagId` (AL-1); Tag is a «E» with its own lifecycle (see Doc 05 §5.4)."

---

## Second-Pass Findings Summary

| ID | Category | Severity | Status |
|---|---|---|---|
| N-01 | Data Contract Gap (Doc 09 users table) | HIGH | ✅ **RESOLVED** — `weekly_digest_enabled BOOLEAN NOT NULL DEFAULT false` + `idx_users_weekly_digest` added to Doc 09 §2.1 |
| N-02 | Data Contract Gap (Doc 09 refresh_tokens) | MEDIUM | ✅ **RESOLVED** — `family_id UUID NOT NULL` (family-scoped revocation) + `idx_refresh_tokens_family_id` added to Doc 09 §2.3 |
| N-03 | Domain/API Mismatch (Doc 06 §2.1 tags type) | LOW | ✅ **RESOLVED** — Notes updated to "Cross-aggregate Tag references by TagId (AL-1); Tag is an Entity managed by TagManagementService." in Doc 06 §2.1 |
| M-03 | Domain Event Mismatch (Doc 08 §4.10 Income) | MEDIUM | Deferred with Income context |
| S-07 | Security Gap (GET query param token) | LOW | Accepted pattern, acknowledged |
| S-08 | Security Gap (headers not in API contract) | LOW | Cross-reference only needed |

**All blocking findings resolved. Deferred/accepted items (M-03, S-07, S-08) require no action before planning.** Specification set is clear for `/speckit-plan`.

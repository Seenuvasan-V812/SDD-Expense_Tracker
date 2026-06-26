# AI-Agent Instruction Pack Quality Checklist: Daily Expense Application

**Purpose**: Validate the AI-Agent Instruction Pack for completeness, determinism, and constitutional fidelity before it is used as the coding agent's System Prompt
**Created**: 2026-06-25
**Document**: [11-agent-instruction-pack.md](../11-agent-instruction-pack.md)

## Content Quality

- [x] Written as direct, imperative commands to the AI coding agent (not prose/explanation)
- [x] Highly deterministic language; LLM-parsable formatting (bold, tables, checklists)
- [x] No invented law — every rule traces to a Constitution clause (P/AL/API/SEC/CQ/FE) or spec contract (07/09/10)
- [x] All five mandated sections present and in order

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] **§1 Persona & Prime Directive**: Senior Java/React engineer bound to the Constitution; forbids conversational tone, apologizing, and skipping validation
- [x] **§2 Context Loading Sequence**: exact ordered read list (Constitution → Glossary → Domain → Aggregate → API → Data → Security → Events → Task) with stop conditions, referencing real files 01–10
- [x] **§3 The 3-Commit Loop**: RED (failing Testcontainers/Mockito test) → GREEN (minimal Spring Boot/React) → REFACTOR (MDC tracing + clean DTO mapping), each a distinct commit with message format
- [x] **§4 Hard-Stop Violation Triggers**: checklist of forbidden patterns including the four required ones (null from service, JPA entity in controller payload, `any` in TypeScript, omitting `Idempotency-Key`/`traceId`) plus full backend/frontend coverage, each mapped to a clause
- [x] **§5 Self-Correction Protocol**: do-not-apologize, read the failure, locate the violated clause, state one-line diagnosis, apply exact fix, re-run checks, resubmit — with a worked example
- [x] Rules are testable/unambiguous (each trigger has a compliant counter-form and clause id)
- [x] Scope clearly bounded to the five core contexts / specs 01–10
- [x] Dependencies and assumptions identified (Appendix B)

## Feature Readiness

- [x] Consistent with API contract (07): versioning, pagination/error envelopes, status codes, DTO boundary, 403-never-404
- [x] Consistent with data contract (09): NUMERIC(19,4) money, no cross-service FKs, audit columns, indexed `user_id`, idempotency guards
- [x] Consistent with security spec (10): token lifecycle, ownership, PII masking, receipt upload constraints
- [x] Quick Compliance Card (Appendix A) gives the agent a single pinnable clause lookup
- [x] No new law introduced; Constitution remains supreme (stated explicitly)

## Notes

- **`Idempotency-Key`** is not in the current Constitution/API contract; the user explicitly required it as a hard-stop trigger. It is included as an architectural convention realizing the existing once-per-period/uniqueness idempotency intent (REQ-BUD-006; `fired_*` and unique constraints in 09), and Appendix B flags that the OpenAPI spec must be updated to declare the header (P1). This is the only additive element and is documented as an assumption, not a silent invention.
- The pack is a derived instruction artifact: where it reads stricter than the Constitution it is operationalizing an existing `MUST`; it adds no law.
- All checklist items pass; document is ready for `/speckit-plan`.

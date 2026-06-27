# Prompt Guide — SpecKit `plan` → `tasks` → `analyze`

> **Project:** Daily Expense Application
> **Feature directory:** `specs/001-daily-expense-tracker`
> **Purpose:** Ready-to-paste prompts for the next three SpecKit phases, tuned to *this*
> repository's customized wiring. Copy each slash command + its argument block into Claude Code.
>
> This guide covers **plan**, **tasks**, and **analyze** only. The `implement` phase prompt
> will be added later.

---

## 0. Why these prompts are customized (read first)

Three things in this repo change what the stock SpecKit commands do by default. The prompts
below are written to neutralize all three.

### Gotcha 1 — `spec.md` is a MANIFEST, not the spec
`spec.md` holds only summaries plus a **Section 6** table that orders the 14 detailed files
(`01`–`14`) and the constitution. The stock `/speckit-plan` skill reads **only** `spec.md` +
`constitution.md`. It will **not** open your numbered files unless the prompt explicitly tells
it to follow the Section 6 manifest. The plan prompt forces this.

### Gotcha 2 — File names are remapped in `.specify/scripts/powershell/common.ps1`
| SpecKit variable | Conventional file | This repo resolves to | Exists today? |
|---|---|---|---|
| `FEATURE_SPEC` | `spec.md` | `spec.md` | ✅ |
| `IMPL_PLAN` | `plan.md` | `plan.md` | ❌ → created by `/speckit-plan` |
| `TASKS` | `tasks.md` | **`12-implementation-plan.md`** | ✅ (hand-written) |

`.specify/feature.json` correctly points at `specs/001-daily-expense-tracker`, so the scripts
resolve. `setup-plan.ps1` copies `plan-template.md` → `plan.md` (because `plan.md` is absent),
then the skill fills it.

### Gotcha 3 — `/speckit-tasks` output-file conflict
The `speckit-tasks` skill text says "Generate **tasks.md**", but `check-prerequisites.ps1`,
`/speckit-analyze`, and `/speckit-implement` are all wired (via `common.ps1`) to read
**`12-implementation-plan.md`** as the tasks artifact. Run unguided, the command would create a
stray `tasks.md` that the rest of the toolchain ignores. You already have a hand-built
`12-implementation-plan.md` (T001–T111 + review gates) **and** `13-task-breakdown.md`
(atomic TASK-001..111). The tasks prompt redirects output to the wired file and reconciles
instead of duplicating.

### Scope guard — `spec.md` Section 7 (Phase-2 deferrals)
Phase 1 = **user-service, category-service, expense-service, savings-goal-service,
budget-service** only. `income-service`, `reporting-service`, and the **notification consumer**
are deferred. Active services still **write** notification/reporting events to the transactional
outbox, but there is **no consumer** in scope. Any plan/task referencing deferred items as
buildable work is invalid.

---

## 1. Pre-flight check (run once, before `/speckit-plan`)

```
.specify/scripts/powershell/check-prerequisites.ps1 -PathsOnly
```

**Expected output (verify before continuing):**
- `FEATURE_DIR` ends with `specs/001-daily-expense-tracker`
- `IMPL_PLAN` ends with `...\plan.md`
- `TASKS` ends with `...\12-implementation-plan.md`

If `FEATURE_DIR` is wrong, set it before proceeding:
`.specify/feature.json` must contain `{ "feature_directory": "specs/001-daily-expense-tracker" }`.

---

## 2. Phase: `/speckit-plan`

### Command + argument

```
/speckit-plan
```

```
CONTEXT: spec.md in this feature is an ENTRY POINT / MANIFEST only — it holds summaries.
Before writing anything into plan.md you MUST read, in the exact order listed in spec.md
Section 6, all of these authoritative sources and treat them as binding:
  1. .specify/memory/constitution.md (v1.1.2)
  2. 01-context-specification.md
  3. 02-glossary.md
  4. 03-requirement-catalogue.md
  5. 05-domain-model.md
  6. 06-aggregate-specifications.md
  7. 07-api-specification.md
  8. 08-event-catalog.md
  9. 09-data-contract-specification.md
  10. 10-security-specification.md
  11. 04-feature-specifications.md
  12. 11-agent-instruction-pack.md
  13. 12-implementation-plan.md
  14. 13-task-breakdown.md
  15. 14-test-strategy.md
Detail in these files OVERRIDES any inference from spec.md. Do not paraphrase or weaken them.

SCOPE GUARD (spec.md Section 7): Phase 1 = user-service, category-service, expense-service,
savings-goal-service, budget-service ONLY. Do NOT plan income-service, reporting-service, or
the notification CONSUMER. Active services still WRITE notification/reporting domain events to
the transactional outbox — model that, but plan no consumer. Any milestone referencing deferred
items is invalid and must be omitted.

DECISIONS ARE ALREADY MADE — do not re-open them. The tech stack (Java 21 / Spring Boot 3.x,
PostgreSQL one-DB-per-service + Flyway, Kafka transactional outbox, MinIO, React 18 + TS strict,
JWT HS256 15m/7d rotating refresh, JUnit5/Mockito/Testcontainers) is fixed by the constitution
and spec.md Section 4. Do NOT propose alternatives. Use "NEEDS CLARIFICATION" ONLY for genuine
gaps not resolved anywhere in the 15 sources above — minimize these.

OUTPUTS:
- plan.md: fill Technical Context from the sources; complete the Constitution Check gate against
  AL-1..AL-5, API-1..API-7, SEC-1..SEC-6, CQ-1..CQ-14, FE-1..FE-6, P1..P7 and ERROR on any
  unjustified violation. Align the plan's phase structure with the existing 12-implementation-plan.md
  (Phase 0 shared-kernel -> Phase 1 user -> Phase 2 category+expense -> Phase 3 savings+budget ->
  Phase 4 outbox infra -> Phase 5 frontend) and the monorepo layout in its Section 1.
- research.md: only for any real NEEDS CLARIFICATION; cite which source settles each.
- data-model.md: derive from 05-domain-model.md (aggregates, VOs, INV-1..INV-10) and
  09-data-contract-specification.md (16 tables across 5 DBs + outbox + processed_events).
- contracts/: derive from 07-api-specification.md (51 endpoint groups, pagination + error
  envelopes, status-code contract, S-08 security headers). One contract file per service.
- quickstart.md: derive end-to-end validation scenarios from 04-feature-specifications.md (BDD).

Use canonical names from 02-glossary.md exclusively; anti-terms are prohibited. Re-run the
Constitution Check after design and report any residual gate risk.
```

### Expected outcomes
- **`plan.md`** created in the feature directory, with a completed **Technical Context** and a
  **Constitution Check** gate that passes (or lists explicitly justified exceptions). Phase
  structure mirrors `12-implementation-plan.md` (Phase 0→5).
- **`research.md`** — short; ideally few/zero `NEEDS CLARIFICATION` because the stack is pinned.
- **`data-model.md`** — aggregates, value objects, invariants `INV-1..INV-10`, and the 16-table
  schema across 5 databases (+ `outbox` / `processed_events` per service).
- **`contracts/`** — one contract file per service derived from the 51 endpoint groups, with the
  pagination + error envelopes and security headers.
- **`quickstart.md`** — runnable end-to-end validation scenarios from the BDD specs.
- **`CLAUDE.md`** — the `<!-- SPECKIT START --> ... <!-- SPECKIT END -->` block is updated to
  point at `plan.md`.

### Verify before moving on
- [ ] `plan.md` exists and references your numbered files (proof the manifest was read).
- [ ] No milestone mentions income / reporting / notification **consumer**.
- [ ] Constitution Check section present and green.
- [ ] Glossary terms used; no anti-terms.

---

## 3. Phase: `/speckit-tasks`

> **Decision (Gotcha 3):** write the task list to **`12-implementation-plan.md`** — the file the
> rest of the toolchain reads — and reconcile with the existing content rather than creating a
> stray `tasks.md`.

### Command + argument

```
/speckit-tasks
```

```
OUTPUT TARGET — IMPORTANT: In this repo, common.ps1 maps the SpecKit TASKS artifact to
12-implementation-plan.md (NOT tasks.md). /speckit-analyze and /speckit-implement read
12-implementation-plan.md. Therefore write the generated task list to 12-implementation-plan.md,
not to a new tasks.md. Preserve its existing review-gate structure (Section 3 Definition of Done)
and reconcile rather than discard the existing T001-T111.

SOURCES (read before generating): plan.md and its design artifacts (data-model.md, contracts/,
research.md, quickstart.md), spec.md Section 1 user stories US-01..US-07 with priorities, the
atomic breakdown in 13-task-breakdown.md (TASK-001..111 with REQ traceability + Depends-On), the
existing 12-implementation-plan.md phasing, and 14-test-strategy.md for the test tasks.
Load .specify/memory/constitution.md for the per-task quality gates.

ORGANIZATION: Keep tasks dependency-ordered and grouped by the existing phase model
(Phase 0 shared-kernel -> 1 user -> 2 category+expense -> 3 savings+budget -> 4 outbox -> 5 frontend).
Map each task to its user story (US-01..US-07) and REQ-* / aggregate-invariant ID from
13-task-breakdown.md. Mark [P] only where files are disjoint and dependencies are satisfied.
Every task MUST carry: checkbox, sequential ID, [P] where parallel, story/REQ label, and an exact
file path under the monorepo layout in 12-implementation-plan.md Section 1.

TDD IS REQUIRED here (constitution 3-Commit Loop): for each implementation task emit the RED test
task first — Mockito unit per service class, Testcontainers integration per endpoint group covering
happy path + 400/401/403/404/409 — per 14-test-strategy.md and SC-05.

SCOPE GUARD: Phase-1 services only. No income/reporting/notification-consumer tasks. Producers
still emit outbox events. Do not generate tasks for spec.md Section 7 deferred items.
```

### Expected outcomes
- **`12-implementation-plan.md`** refreshed: a dependency-ordered, story-labeled task list in the
  required checklist format (`- [ ] T### [P?] [US?] description — exact/file/path`), with the
  review gates preserved.
- Each implementation task is preceded by its **RED test task** (3-Commit Loop).
- Every task carries a user-story (`US-01..US-07`) and `REQ-*` / invariant trace ID.
- A **dependencies / parallel-execution** summary and an **MVP scope** suggestion (typically
  User Story 1 / the user-service slice).

### Verify before moving on
- [ ] Output landed in `12-implementation-plan.md` (NOT a new `tasks.md`).
- [ ] Every task has checkbox + ID + label + concrete file path.
- [ ] No tasks for deferred Phase-2 contexts.
- [ ] Test tasks present per service class and per endpoint group.

> **Alternative (not recommended):** if you prefer to leave `12-implementation-plan.md` untouched
> and generate a fresh `tasks.md`, change the first paragraph to "write to tasks.md", then pass
> `/speckit-analyze` an argument pointing it at `tasks.md`. This splits your source of truth and
> desyncs the toolchain — avoid unless you intend to re-wire `common.ps1`.

---

## 4. Phase: `/speckit-analyze`

> **Read-only.** This command modifies nothing. It is hard-wired (modified skill, line 78) to
> compare `spec.md` ↔ `plan.md` ↔ **`12-implementation-plan.md`**, and it requires `plan.md` to
> exist — so run `/speckit-plan` first.

### Command + argument

```
/speckit-analyze
```

```
Run the standard read-only cross-artifact consistency analysis over spec.md, plan.md, and
12-implementation-plan.md, with the project constitution as non-negotiable authority.

Because spec.md is a MANIFEST, resolve its requirements through the numbered detail files it
points to (03-requirement-catalogue.md for REQ-* IDs and MoSCoW/phase split, 05/06 for domain
invariants, 07 for the 51 endpoint groups, 08 for the 45 events, 09 for schema, 10 for security
controls, 14 for test coverage). Build the requirements inventory from REQ-* and SC-01..SC-05.

Prioritize these checks for THIS project:
- Coverage: every Phase-1 REQ-* and each SC-01..SC-05 maps to >=1 task; flag zero-coverage reqs.
- Scope leakage: any plan.md milestone or task targeting income/reporting/notification-consumer
  (spec.md Section 7 deferrals) is CRITICAL — producers may emit outbox events, but no consumer.
- Constitution MUST violations (AL-1 cross-schema access, API-3 error envelope, SEC-3 403-never-404,
  CQ-2 Optional/no-null, CQ-8 transactional outbox, DB-5 Money type) -> automatically CRITICAL.
- Terminology drift vs 02-glossary.md canonical names (flag any anti-terms in plan/tasks).
- Task hygiene: missing file paths, missing US/REQ labels, ordering contradictions (e.g. integration
  before its foundational setup), and untestable acceptance criteria.

Produce the compact findings table + coverage summary + metrics. Do NOT modify any file. End with
Next Actions and offer remediation; apply nothing without my explicit approval.
```

### Expected outcomes
- A **findings table** (ID, Category, Severity, Location, Summary, Recommendation), capped at 50
  rows with overflow summarized.
- A **coverage summary** mapping each requirement key → task IDs.
- **Constitution-alignment** issues, **unmapped tasks**, and a **metrics** block
  (total reqs, total tasks, coverage %, ambiguity/duplication counts, critical count).
- A **Next Actions** block and an offer to draft remediation edits (applies nothing automatically).

### Act on the report
- [ ] Resolve all **CRITICAL** and **HIGH** findings before `/speckit-implement`.
- [ ] Re-run `/speckit-analyze` after fixes to confirm a clean(er) report.

---

## 5. Recommended order & master checklist

1. **Pre-flight** — `check-prerequisites.ps1 -PathsOnly` resolves the right paths.
2. **`/speckit-plan`** — creates `plan.md` + design artifacts; updates `CLAUDE.md`.
3. **`/speckit-tasks`** — refreshes `12-implementation-plan.md` (the wired TASKS file).
4. **`/speckit-analyze`** — read-only report; fix CRITICAL/HIGH; re-run.
5. *(later)* `/speckit-implement` — prompt to be added.

**Cross-cutting watch-list**
- **Manifest read:** if plan output ignores the numbered files, Section 6 was skipped — re-run.
- **Output file:** confirm `/speckit-tasks` wrote to `12-implementation-plan.md`, not `tasks.md`.
- **Deferral guard:** scan plan + tasks for income / reporting / notification-consumer work and
  remove it; outbox *producers* are allowed.
- **Glossary:** canonical names only; anti-terms are violations.

---

*End of PROMPT-GUIDE.md — covers plan, tasks, analyze. Implement phase to follow.*

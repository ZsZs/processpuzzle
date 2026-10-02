# ProcessPuzzle Architecture Review Rubric

Shared by every slice review and by the final synthesis pass. Do not change scales mid-review; if a scale needs changing, re-score all existing reports.

Suggested location: `docs/arch-review/RUBRIC.md`. Reports go to `docs/arch-review/findings/<slice>.md`.

---

## 1. Ground rules

- **A slice** is one feature and one side: backend or frontend (e.g. `base-workflow-backend`, `base-workflow-frontend`). Review only the paths named in the prompt.
- **Report architecture debt only**: violated or missing boundaries, wrong responsibilities, harmful coupling, missing abstractions, duplication that will hurt, risks hidden by test gaps. Do not report style, naming, or formatting unless it causes an architectural problem.
- **Evidence or it does not exist.** Every finding needs at least one `file:line` reference or tool output. No speculative findings.
- **Do not modify code.** Read, run analysis commands, write the report.
- **Anything outside the slice** goes to the "Out-of-scope observations" list, unscored.
- **Stated intent is the yardstick**: hexagonal architecture, Spring Modulith module boundaries (backend); signals-first Angular and Nx module boundaries (frontend); metadata-driven behavior over hard-coded behavior (platform). A finding is a gap between intent and reality, or a flaw in the intent itself (say which).

---

## 2. Finding schema

Use exactly this structure for every finding.

```markdown
### <ID> — <short title>

- **Category:** <code from section 3>
- **Criticality:** <1-5>
- **Blast radius:** <1 slice | 2 feature | 3 platform>
- **Effort:** <S | M | L | XL>
- **Confidence:** <High | Medium | Low>
- **Priority score:** <criticality × blast ÷ effort points, 1 decimal>
- **Evidence:**
  - `path/to/File.java:123` — what is there
  - <tool output line, if any>
- **Why it is debt:** 2-4 sentences. What intent it violates and what it costs today or later.
- **Suggested refactoring:** concrete steps, not "clean this up".
- **Depends on / blocks:** <other finding IDs, or "none">
```

**ID format:** `<SLICE>-<CATEGORY>-<nn>`, e.g. `BWF-BND-01`. Define a short slice code once at the top of each report.

**Confidence:** High = verified in code or tool output. Medium = strong indication, not fully traced. Low = plausible, needs a human look. Low-confidence findings are listed, but their priority score is shown in brackets, e.g. `[2.0]`.

---

## 3. Categories

| Code | Category | Typical examples |
|------|----------|------------------|
| BND | Module boundary violation | Modulith module reaching into another module's internals; Nx lib importing a forbidden tag; deep imports past a public API |
| LAY | Hexagonal layer violation | Domain depending on framework or infrastructure; adapters containing business rules; controllers calling repositories directly |
| CYC | Cyclic or tangled dependency | Module or library cycles, event loops between modules, barrel-file cycles |
| ABS | Missing or leaky abstraction | Port missing, implementation type exposed through an API, generic mechanism duplicated per feature |
| PER | Persistence leak or misuse | JPA entities used as API or domain models, EAV/JSONB access scattered outside its owning layer, cross-module joins |
| API | Contract problem | Code and OpenAPI out of sync, inconsistent error model, unversioned breaking changes, DTO/domain mixing |
| DUP | Harmful duplication | Same logic or wiring in several features that will need to change together |
| MET | Metadata-driven consistency | Behavior hard-coded where the platform promises metadata configuration; metadata schema inconsistently interpreted |
| STA | Frontend state and reactivity | Signals mixed with ad hoc RxJS state, state owned by the wrong layer, effects doing business logic |
| SEC | Security architecture | Authorization enforced in the wrong layer, missing tenant or org isolation, secrets or roles hard-coded |
| TST | Test architecture gap | Critical logic without tests at the right level, tests coupled to internals, missing boundary tests (ArchUnit, Modulith `verify`, Nx lint) |
| OPS | Operability and evolution | Missing observability hooks, migrations without a strategy, configuration scattered, no upgrade path |

If a finding fits two categories, pick the one that names the root cause and mention the other in "Why it is debt".

---

## 4. Criticality (1-5)

| Score | Meaning |
|-------|---------|
| 5 | Security or data-integrity risk, or a defect in the foundation that will force a rewrite if left. Fix before building on it. |
| 4 | Blocks or seriously distorts the roadmap (e.g. customer seed, multi-tenancy, workflow engine growth). Every new feature pays for it. |
| 3 | Slows a whole feature or team workflow; workarounds exist but spread. |
| 2 | Local pain: contained in one area, annoying, but stable. |
| 1 | Cosmetic or hygiene; fix opportunistically. |

Score the cost of leaving it as is, not the effort of fixing it.

---

## 5. Blast radius

| Value | Meaning | Weight |
|-------|---------|--------|
| slice | Confined to the reviewed slice | 1 |
| feature | Affects the feature's other side, or other slices that depend on it | 2 |
| platform | Affects several features or apps, or a shared foundation | 3 |

Foundation slices (base-entity, base-state, shared/core libs) will often score `platform`. Say why when you pick it.

---

## 6. Effort

| Size | Points | Meaning (one developer, familiar with the code) |
|------|--------|--------------------------------------------------|
| S | 1 | Under half a day. Mechanical, low risk. |
| M | 2 | About 1-3 days. One module, tests adjusted. |
| L | 4 | About 1-2 weeks. Several modules, migration or API change involved. |
| XL | 8 | More than 2 weeks. Cross-cutting, needs a design step or staged rollout. |

Estimates are for ordering and rough size. The day ranges are a starting assumption; recalibrate after estimating one or two items yourself (see section 9).

Include in the estimate: code change, test updates, and migration or data steps. Exclude: review waiting time.

---

## 7. Priority score

```
priority = criticality × blast weight ÷ effort points
```

Examples:

- Criticality 5, platform (3), effort M (2) → 7.5
- Criticality 3, slice (1), effort S (1) → 3.0
- Criticality 4, feature (2), effort XL (8) → 1.0

Sort descending. A low score on a high-criticality, XL item means "plan it as a project", not "ignore it". The synthesis pass flags these separately as **strategic debts** (criticality ≥ 4 and effort ≥ L).

---

## 8. Report layout (one file per slice)

```markdown
# Architecture review: <slice>

- **Slice code:** <e.g. BWF>
- **Paths in scope:** ...
- **Reviewed at commit:** <sha>
- **Date:** <yyyy-mm-dd>
- **Tools run:** <ArchUnit, Modulith verify, nx lint, madge ... and result summary>

## Summary
3-6 sentences: overall health, the main themes, the top three findings.

## Findings table
| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
(sorted by priority, descending)

## Findings
(one section per finding, using the schema in section 2)

## Out-of-scope observations
Bullet list with `file:line` where possible. Unscored.

## Strengths worth keeping
2-5 bullets on structures that work well and should be replicated elsewhere.
```

---

## 9. Calibration

Before the first full run, estimate 2-3 known debts yourself using sections 4-6, and have the reviewer score the same ones. If the reviewer's effort is consistently off in one direction, adjust the day ranges in section 6 once, and note the change and date here.

| Date | Change | Reason |
|------|--------|--------|
| | | |

---

## 10. Synthesis pass (final run)

Input: all reports in `docs/arch-review/findings/`. Do not re-read source code unless a finding is contradictory.

1. Merge duplicates and near-duplicates across slices; keep the most precise evidence and list all affected slices.
2. Re-score blast radius where the same debt appears in several slices (it usually becomes `platform`).
3. Group findings into systemic themes (e.g. "persistence details leak in 5 features") with a one-paragraph root cause each.
4. Produce the ranked list by priority score, then a separate list of strategic debts.
5. Propose a refactoring sequence that respects "depends on / blocks" links, and mark what can run in parallel.
6. Output to `docs/arch-review/SUMMARY.md`.

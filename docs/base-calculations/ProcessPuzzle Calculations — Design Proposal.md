# ProcessPuzzle Calculations — Design Proposal

Oct 5, 2026 · @Zsolt

## Name and scope

Call the feature **base-calculation**, in line with base-entity, base-state and base-workflow, with the usual `-backend`, `-api` and `-frontend` suffixes. It is the library that turns entity data into derived values, aggregates and rankings, defined entirely as metadata.

| Option | Fit | Verdict |
| --- | --- | --- |
| base-calculation | Names what users configure and what both examples need (totals, rankings) | Recommended |
| base-decision | DMN's own term; broader, and overlaps with your existing rules | Possible later umbrella if rules merge in |
| base-formula | Suggests row-level formulas only; misses aggregation and ranking | Skip |

**In scope:** row derivations, aggregation, ranking, lookup tables, triggers, persisted and frozen results, a designer. **Out of scope for v1:** BI-style reporting, general scripting, AI generation (a later layer that targets the same schema).

## Goals

ProcessPuzzle gets calculations that are as declarative as its entities, states and workflows: a user describes what to compute, never how.

1. **Generic:** one mechanism serves the sailing race (durations, ranking, discards) and sales (totals, averages, tiers).
2. **Declarative:** definitions are metadata (YAML), validated and analyzable; no embedded code.
3. **Business-level abstraction:** decision tables and a restricted expression language, readable by a domain expert.
4. **Integrated:** inputs come from entity queries; triggers come from states and workflows; results land in entities.
5. **Auditable:** a result can be frozen and remains explainable after the definition changes.
6. **AI-ready, not AI-dependent:** the schema is complete without AI; a generator can target it later.

**Non-goals:** a general programming language, a BI tool, or replacing the existing rules feature.

## Concept and reasoning

A calculation is a small graph of decisions in DMN style, evaluated with FEEL expressions over entity queries. DMN supplies the semantic model, and the platform adds what DMN lacks.

**Three kinds of calculation** must be covered:

| Kind | Sailing example | Sales example |
| --- | --- | --- |
| Row-level derivation | Elapsed time = finish minus start | Line total = quantity times price |
| Aggregation over related records | Total points per boat | Order total, monthly average |
| Ordered or windowed | Rank per round, drop worst round, tie-breaks | Rank customers by revenue |

**Building blocks, taken from DMN:** input data, decisions, decision tables with hit policies, reusable business knowledge models (functions), and the requirements graph that orders them. FEEL provides durations, decimal arithmetic and list functions.

**Platform additions, because DMN does not cover them:**

- Entity query binding for inputs (filter, group, order over BaseEntity).
- A function library for group-by, rank with ties and top/drop-N, exposed inside FEEL.
- Triggers from state and workflow transitions, and persistence of results into entities.
- A materialization policy per calculation: computed on read, or computed on an event and stored.
- Definition versioning, so frozen results stay explainable.

**Why not JavaScript views:** code inside metadata cannot be validated, analyzed for dependencies, edited in a designer or safely generated. A restricted, side-effect-free expression language keeps the abstraction above programming-language level.

**Why YAML with DMN semantics, not DMN XML:** the platform already defines starters in YAML. Import and export of DMN XML can come later without changing the model.

## API

The API has two halves: managing definitions, and running them. Paths are illustrative and should follow the conventions of the existing `base-workflow-api.yaml`.

| Operation | Endpoint | Purpose |
| --- | --- | --- |
| Manage definitions | `GET/POST/PUT /calculation-definitions` | CRUD with versioning; each save creates a new version |
| Validate | `POST /calculation-definitions/validate` | Static check: types, references, cycles; returns the dependency graph |
| Evaluate | `POST /calculations/{id}/evaluate` | Compute on demand for given parameters; nothing is stored |
| Run | `POST /calculations/{id}/runs` | Compute and persist results into the output entity |
| Read a run | `GET /calculation-runs/{runId}` | Status, results, definition version, input snapshot reference |
| Explain | `GET /calculation-runs/{runId}/trace` | Intermediate value of every decision, for audit and debugging |
| Freeze | `POST /calculation-runs/{runId}/freeze` | Make the run's results immutable |

**Events published** (for the workflow and state engines): `CalculationCompleted`, `CalculationFailed`, `CalculationFrozen`.

**Shape of a definition** (full examples follow below):

```yaml
calculation: <id>
version: <n>
parameters: {}      # e.g. which race or period to calculate
inputs: {}          # named entity queries
tables: {}          # decision tables (lookups)
functions: {}       # reusable business knowledge (FEEL)
decisions: {}       # the graph; each decision lists its requirements
output: {}          # target entity, mapping, materialization
trigger: {}         # state or workflow transition, or manual
```

## Examples as metadata

Both examples use the same schema. Each decision has exactly one logic type: `expr` (a FEEL expression), `table` (a decision table), `aggregate`, or `rank`. The `aggregate` and `rank` types are the platform's group and window operators; their exact syntax here is a proposal to test, not a settled design.

### Sailing: series ranking

The scoring below is a simplified low-point system for illustration. The real discard and tie-break rules come from each race's sailing instructions and the racing rules, and need to be confirmed with the sailing domain before modelling.

```yaml
calculation: sail-series-ranking
version: 1
parameters:
  series: { entity: Series }
inputs:
  finishes:
    entity: Finish            # boat, round, startTime, finishTime, status
    where: "series = ${series}"

tables:
  round-points:
    hitPolicy: FIRST
    inputs: [status, position, starters]
    output: points
    rules:
      - { when: ['"DNF", "DNS", "DSQ"', '-', '-'], then: "starters + 1" }
      - { when: ['-', '-', '-'],                  then: "position" }
  discards:
    hitPolicy: FIRST
    inputs: [completedRounds]
    output: discardCount
    rules:
      - { when: ['< 4'],  then: 0 }
      - { when: ['>= 4'], then: 1 }

decisions:
  elapsed:                      # FEEL duration, not seconds
    for: finishes
    expr: "finishTime - startTime"
  position:
    for: finishes
    requires: [elapsed]
    rank: { by: elapsed, order: asc, partitionBy: round, filter: 'status = "OK"' }
  starters:
    for: finishes
    aggregate: { count: boat, partitionBy: round }   # broadcast to each row
  roundPoints:
    for: finishes
    requires: [status, position, starters]
    table: round-points
  completedRounds:
    aggregate: { countDistinct: round }
  discardCount:
    requires: [completedRounds]
    table: discards
  bestRound:
    for: boat
    requires: [position]
    aggregate: { min: position, partitionBy: boat }
  seriesTotal:
    for: boat
    requires: [roundPoints, discardCount]
    aggregate: { sum: roundPoints, partitionBy: boat, dropWorst: discardCount }
  seriesRank:
    for: boat
    requires: [seriesTotal, bestRound]
    rank: { by: seriesTotal, order: asc, tieBreak: [bestRound] }

output:
  entity: SeriesResult
  mapping: { boat: boat, total: seriesTotal, rank: seriesRank }
  materialization: stored
trigger:
  state: { entity: Series, to: Finished }
  freeze: onCompletion
```

### Sales: order total with tiered discount

```yaml
calculation: order-total
version: 1
parameters:
  order: { entity: Order }
inputs:
  lines:    { entity: OrderLine, where: "order = ${order}" }
  customer: { entity: Customer,  where: "id = ${order.customer}" }

tables:
  volume-discount:
    hitPolicy: FIRST
    inputs: [subtotal, customerTier]
    output: discountRate
    rules:
      - { when: ['>= 10000', '"GOLD"'], then: 0.10 }
      - { when: ['>= 10000', '-'],       then: 0.07 }
      - { when: ['>= 1000',  '-'],       then: 0.03 }
      - { when: ['-', '-'],              then: 0 }

decisions:
  lineTotal:
    for: lines
    expr: "quantity * unitPrice"
  subtotal:
    requires: [lineTotal]
    aggregate: { sum: lineTotal }
  customerTier:
    expr: "customer.tier"
  discountRate:
    requires: [subtotal, customerTier]
    table: volume-discount
  total:
    requires: [subtotal, discountRate]
    expr: "round half up(subtotal * (1 - discountRate), 2)"

output:
  entity: Order
  mapping: { total: total }
  materialization: stored
trigger:
  onChange: { entity: OrderLine }     # recompute while the order is open
  freeze: { state: { entity: Order, to: Confirmed } }
```

The two examples differ in trigger and freezing (a one-time result when a series finishes, versus a live value that freezes on confirmation) but share every other concept. A monthly average is the same pattern with a period parameter and `aggregate: { avg: ... }`.

## Open questions and build order

**Build order**, with the sailing and sales examples as acceptance tests:

1. Definition schema in YAML plus static validation (types, references, cycles).
2. Engine port in the hexagonal backend, with a FEEL adapter behind it.
3. Entity query binding for inputs.
4. Platform function library: group, rank with ties, drop-N.
5. Run persistence, triggers from state and workflow, freezing and trace.
6. Frontend designer, starting with the decision table editor.

**Open questions:**

- [ ] Which FEEL engine for the JVM, and which for the browser? Evaluate maturity, licence and custom-function support in a short spike.
- [ ] Should the browser evaluate calculations (live previews in the designer) or only call the backend?
- [ ] Is one engine on both sides required, so results can never differ between preview and run?
- [ ] How are definition changes handled for open (not yet frozen) results: recompute automatically, or on request?
- [ ] Which entities and attributes of the EAV/JSONB layer need to be exposed to queries, and how are they typed for FEEL (decimal, duration, date-time)?
- [ ] Does base-calculation eventually absorb the existing rules feature, or stay separate?

# base-calculation-backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-calculation-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_calculation_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_calculation_backend)

## Introduction

`base-calculation-backend` turns entity data into derived values, aggregates and rankings, defined as metadata.
A calculation is a small graph of decisions in DMN style, written as YAML and evaluated with FEEL by
[Camunda's feel-scala](https://github.com/camunda/feel-scala) (Apache 2.0). The design is
[the proposal](../../../docs/base-calculations/ProcessPuzzle%20Calculations%20—%20Design%20Proposal.md).

It is a Spring Modulith application module (`calculation`) that depends on `core` and `shared` only. It names no
other feature: entity data is reached through the outbound ports of `calculation :: port`, which the host
implements — see `CalculationEntityAdapter` and `CalculationAttributeTypeAdapter` in the testbed backend.

## A definition

```yaml
calculation: order-total               # the id
name: Order total
parameters:
  order: { entity: order }             # an entity parameter takes an object id
  customerTier: { type: string, default: STANDARD }
inputs:
  lines: { from: order, path: lineItems, entity: order-line }   # rows embedded in a parameter's object
  # finishes: { entity: finish, where: "series==${series}" }    # or an RSQL query
tables:
  volume-discount:
    hitPolicy: FIRST                   # FIRST, UNIQUE, ANY or COLLECT
    inputs: [subtotal, customerTier]
    output: discountRate
    rules:
      - { when: ['>= 10000', '"GOLD"'], then: 0.10 }
      - { when: ['-', '-'],              then: 0 }
functions:
  gross: "function(net) net * 1.2"
decisions:
  lineTotal:    { for: lines, expr: "quantity * unitPrice" }      # one value per row
  subtotal:     { aggregate: { sum: lineTotal } }                 # one value
  discountRate: { table: volume-discount }
  total:        { expr: "round half up(subtotal * (1 - discountRate), 2)" }
output:
  entity: order
  mapping: { total: total }
  materialization: stored              # or onRead
trigger:
  onChange: { entity: order }          # also: state: { entity, to }, manual: true
  freeze: { state: { entity: order, to: CONFIRMED } }   # or: onCompletion
```

Each decision has exactly one logic type — `expr`, `table`, `aggregate` or `rank` — and a scope:

| `for` | Scope | A decision there… |
| --- | --- | --- |
| absent | single value | sees the parameters, inputs (as lists of rows), functions and earlier single values |
| an input | per row | also sees the row's columns, and adds its value to the row as a column |
| an attribute, or `input.attribute` | per group | one row per distinct value; sees the group's row and its input's name bound to the group's members |

- `aggregate`: one of `count`, `countDistinct`, `sum`, `avg`, `min`, `max` over a column or a FEEL expression;
  `partitionBy` broadcasts a per-partition value to each row; `dropWorst` / `dropBest` (a number or an expression
  such as a decision's name) leave out values, `better: lower` (default) or `higher` says which are worst; `of`
  names the rows of a single-valued aggregate when its requirements do not.
- `rank`: competition ranking (1, 2, 2, 4) `by` a column, `order: asc|desc`, `partitionBy`, a FEEL `filter` (a row
  failing it gets no rank) and `tieBreak` columns (`name` or `name desc`; default the rank's order).
- Platform FEEL functions: `drop worst(list, n)`, `drop best(list, n)` and `top(list, n)`.

`requires` orders the graph, and so does every decision name an expression mentions — a forgotten `requires`
cannot evaluate a decision before what it reads. The analyzer rejects unknown references, a decision with no or two
logic types, table arity, FEEL syntax errors, ambiguous groupings and cycles, and reports the evaluation order and
the dependency edges (`POST …/calculation-definitions/validate`).

Stored attribute values are typed for FEEL through `EntityAttributeTypePort`: numbers become decimals, `DATE` and
`DATE_TIME` text becomes `java.time`; results are stored as JSON — durations and dates as ISO text.

## Definitions, runs and freezing

Definitions follow the platform's draft/released storage (`DefinitionKinds.CALCULATION`, released after the rules).
The YAML `source` is canonical, comments included; the parsed model is kept next to it as jsonb.
`definitionVersion` moves on with every save that changes the source. A definition change applies to the next run
only — open results are not recomputed on their own.

- `evaluate` computes and stores nothing; a failing decision is answered with the trace up to it.
- A run computes, writes the output and keeps a `CalculationRun`: parameters, values, rows, groups, the trace of
  every decision and a **snapshot of the source** — what keeps a frozen result explainable after its definition
  changed or was deleted. A failed run is kept too.
- A frozen run freezes its subject (the output's target object, else the first entity parameter's): no later run
  of that calculation recomputes it, and a manual one is refused with 409.

Reading, computing and writing run in a transaction of their own, so a group output is written all or nothing and
a failure in the entity layer cannot roll back the run's record.

## Triggers and events

`CalculationTriggerListener` observes `PlatformEvent` after commit, in a transaction of its own, and runs the
**released** calculations a fact concerns, binding the fact's object to the parameter of its entity:
`onChange` on CREATED / UPDATED, `state` and the freeze state on STATE_CHANGED (the freeze state recomputes once
more, then freezes). The output write publishes no event, so a calculation cannot trigger itself.

A run publishes `CalculationCompleted` / `CalculationFailed`, a freeze `CalculationFrozen` (`calculation :: event`),
each also as a `PlatformEvent` of subject type `calculation-run` entering `COMPLETED`, `FAILED` or `FROZEN` — so
base-event's catalog can name it and a workflow start on it.

## Host application

| Port | What the host answers | Testbed adapter |
| --- | --- | --- |
| `EntityQueryPort` | an object by id, the objects an RSQL filter selects | `CalculationEntityAdapter` |
| `EntityWritePort` | write mapped values (version kept, no event), upsert a result per group key | `CalculationEntityAdapter` |
| `EntityAttributeTypePort` | an attribute's type; optional | `CalculationAttributeTypeAdapter` |

`FindCalculationDefinition.storedAttributesOf` (`calculation :: usecase`) tells the host which attributes a released
calculation stores; the testbed's `CalculationManagedAttributesAdapter` hands them to base-entity's
`ManagedAttributesPort`, so a form cannot overwrite a calculated total.

`JsonColumnMappingAutoConfiguration` keeps feel-engine's `jackson-module-scala` out of Hibernate's JSON columns —
registered through Jackson's service loader, it would otherwise make every feature's jsonb maps read back as Scala
maps.

## API and seeding

[`base-calculation-api.yaml`](../api-contracts/src/main/resources/base-calculation-api.yaml):
`/organizations/{orgKey}/calculation-definitions` (CRUD, `revision`, `/validate`, `/import`),
`/calculations/{id}/evaluate`, `/calculations/{id}/runs`, `/calculation-runs[/{runId}[/trace|/freeze]]`.

With `base-calculation.loadDefaultCalculations=true` the module imports
`classpath*:default-calculations/base-calculation/<orgKey>-calculations.yaml` — one calculation per YAML document —
at startup, upserting and releasing what it wrote. Business Starters carry calculations in a `calculations` group.

## Limits of v1

- `upsertByKey` reads every object of the result entity to find the key; fine for a result table, not for millions.
- A calculation's write does not refresh a form that is open on the object; it shows the new value on reload.
- No DMN XML import/export, no browser-side evaluation (the designer previews through `evaluate`).

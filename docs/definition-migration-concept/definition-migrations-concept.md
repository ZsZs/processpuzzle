# Definition Release Migrations — Concept

Status: draft, 2026-10-12
Scope: ProcessPuzzle platform, Design view

## 1. Problem

The designer of an organisation's application maintains definitions: Entity, State, Rule, Workflow (and Document / App) definitions. When these are released as a new version, data created under the previous version may no longer fit:

- a field was renamed, removed, or has a new type
- a new field is required but old records have no value
- a state was renamed, split, or removed while records and workflow instances still sit in it
- an entity was split or merged

The platform therefore needs a **migration set** per release: a description of how existing data is transformed so it fits the new definitions.

## 2. Constraints

- The platform is **no-code**. The designer must not write SQL or scripts.
- The designer thinks in terms of *intent* ("I renamed this field", "I split this state"), not in terms of data operations.
- Data lives in the two-layer EAV/JSONB entity system, so most changes are JSON-path operations, not table alterations.
- Rules use PPCL, which the designer already knows. No second expression language.
- Migrations run per organisation, keyed by `{orgKey}`, in the custom DB.
- Business Starters (prepared definition bundles) will get updates, so the same mechanism must serve starter upgrades.

## 3. Core idea

A migration set is a short, ordered list of **declarative operations** chosen from a fixed catalog. It is authored in three layers:

1. **Generated.** On release, the platform diffs the new definitions against the previous release and proposes the steps. Most changes are unambiguous and need no input.
2. **Confirmed.** Where the diff is ambiguous (typically rename versus delete-and-add), the designer answers a simple question with a click.
3. **Completed.** Where data is missing (a new required field, a type conversion, a value mapping), the designer fills in a small form or table.

Optionally, a fourth layer, a *custom step*, handles what the catalog cannot express. It is out of scope for v1.

## 4. Principles

- **Intent over mechanics.** Operations are named after what the designer did.
- **Fixed vocabulary.** Small catalog, each operation with its own form, validation, and preview.
- **PPCL for computed values.** Any "new value derived from the old record" is a PPCL expression.
- **Preview before apply.** Dry run with counts, before/after samples, and a failure list.
- **Safe by default.** Destructive operations default to archive, not drop. Each step records whether it is reversible.
- **Declarative and storable.** A migration set is a YAML document stored with the release, reviewable and exportable like any other definition.

## 5. Release lifecycle

```
Draft definitions
   │  (designer edits)
   ▼
Create release candidate ──► diff against previous release
   │                              │
   │                              ▼
   │                    suggested migration steps
   │                              │
   │         designer confirms / completes / reorders
   ▼                              ▼
Release candidate + migration set (status: ready)
   │
   ├──► dry run on organisation data  (preview report)
   ▼
Release  ──► migration executes (eager batch or lazy)
   ▼
Release applied (record of what changed, per step)
```

The migration set and the definitions are released **together**. A release without a needed migration is flagged as incomplete.

## 6. Versioning and execution

- Every record carries the **definition release** it was last written under.
- A migration set is defined **per release step** (1.3 → 1.4). A customer who skipped a release runs the chain in order (1.2 → 1.3 → 1.4).
- Two execution modes:
  - **Eager**: batch job migrates all affected records at release time. Used for destructive or structural steps.
  - **Lazy**: records are migrated on read or write. Possible for additive steps (add field, rename field), cheap thanks to JSONB.
- The default is eager for safety and simplicity. Lazy is an optimisation for large data sets, decided per step type.
- Each step logs affected counts, failures, and duration for the audit trail.

## 7. State and workflow data

This is the hardest part and gets its own rule set:

- **Records in a changed state** are remapped through an explicit `remapState` step (old state → new state).
- **In-flight workflow instances** finish on the definition they started with. Only new instances use the new definition. Live remapping is offered as an explicit, advanced option, not a default.
- **Removed states** cannot be released while records still sit in them without a mapping. The release is blocked with a clear message.
- State machine events fired during migration are **suppressed** by default (a migration is not a business event), unless the step says otherwise.

## 8. Safety and trust

- **Dry run** is mandatory before the first execution on an organisation: "1,240 records affected, 3 would fail".
- **Before/after samples** for a few records per step.
- **Failure list** with record id and reason.
- **Failure policy** per step: `fail` (abort and roll back), `skip` (leave record, report), `useDefault`.
- **Transactional batches**, with a snapshot or backup point taken before the first destructive step.
- **Reversibility** is recorded per step. An irreversible step is shown with a warning.

## 9. Relation to other features

- **Business Starters**: a starter update is a release plus a migration set. Customers on "billing v1" upgrade to v2 through the same flow.
- **Customer seed**: a seed created from a starter starts at the starter's current release, so no migration is needed on first import.
- **Testbed**: the daily-reset Testbed is a natural place to run dry runs and verify migration sets before they reach a customer.
- **PPCL**: reused for filters (`where`) and computed values.
- **Calculations feature**: derived values may later reference calculation definitions.

## 10. Out of scope for v1

- Custom code steps
- Live remapping of running workflow instances
- Cross-organisation migrations
- Automatic rollback of already-applied releases (v1 offers snapshot restore only)
- Migration of Document templates beyond field renames

## 11. Open questions

1. Are migration sets editable after a release is applied, or immutable? (Proposal: immutable; corrections are a new release.)
2. Is the snapshot taken per organisation or per entity? (Proposal: per organisation, before the first destructive step.)
3. Where are migration sets stored? (Proposal: next to the definitions in the release bundle, same storage as Business Starters.)
4. How is a lazy migration reconciled with queries and indexes on JSONB paths? (Proposal: lazy only for steps that do not change queried paths.)

## 12. Next documents

- `migration-operation-catalog.md`: operations, YAML shapes, v1 scope
- `migration-design-view.md`: diff algorithm, suggestion model, Design view flow, API sketch

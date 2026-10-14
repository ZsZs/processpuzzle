# Definition Release Migrations — Concept

Status: draft, 2026-10-12; v1 decided 2026-10-14 (§12)
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

## 12. v1 decision (2026-10-14)

The sections above describe the target. A review against the release code (phase 2 of the org release) showed that several assumptions do not hold yet, and that the release already provides most of the machinery. v1 is therefore much smaller than §3–§8 and the companion documents suggest. Where this section and the rest of the documents disagree, this section wins for v1.

### 12.1 What the platform already has

- **One transaction per release.** `ReleaseOrganization` runs every `ReleaseParticipant` in one transaction. A failure rolls everything back, and side effects outside the database wait for the commit.
- **Release checks that refuse.** The participants already emit the cases that need a migration as `ReleaseProblem`s, for example `entity.release.attributeRemoved`, `entity.release.requiredWithoutDefault`, `entity.release.attributeTypeChanged` and `state.release.stateRemovedWithObjects`. These are the *Block* and *Input* classes of the design view document.
- **Release history.** `org_release_items` and `org_release_contents` keep the canonical JSON of each released definition, and `ReleaseHistory.contentAt` rebuilds what was live at a release.
- **JSONB records.** Entity objects store their values in `entity_object.payload`, keyed by attribute code. There is no EAV layer. A field operation is one set-based `UPDATE` per entity.
- **State is an attribute.** An object's state is the state key stored in the payload under the machine's `stateAttributeKey`.
- **PPCL on one record.** `RuleEngine.evaluate(key, expression, Map)` evaluates an expression against a record, so far returning a boolean only.

### 12.2 What the platform does not have

- **Stable ids.** Draft and released rows have different UUIDs. The release diff matches attributes by code and states by key, so a rename is a removal plus an addition.
- **A release per record.** Records do not store the release they were written under.
- **Workflow pinning.** A workflow instance stores only the workflow id and runs on the current released definition.

### 12.3 Decisions

1. **A migration step resolves a release problem.** No separate diff or suggestion engine. Each step names the `ReleaseProblem` (kind, key, error id) it resolves, and the Release section offers a **Resolve** action on that problem.
2. **Three operations in v1:**

   | Operation | Resolves | Effect on the payload |
   |---|---|---|
   | `renameAttribute(entity, from, to)` | `attributeRemoved` together with an attribute added to the same entity | moves the value to the new key |
   | `fillAttribute(entity, attribute, value, only: empty \| always)` | `requiredWithoutDefault`, `attributeNowRequired` | sets a literal value |
   | `mapValues(entity, attribute, mapping, unmapped: keep \| fail)` | `stateRemovedWithObjects`, renamed states, changed option lists | rewrites values; a state machine's state attribute is an ordinary attribute here |

   Everything else stays refused by the release checks, as today.
3. **Rename is confirmed by the designer, not detected.** The Resolve form for a removed attribute asks "Was it renamed to …?" and lists the attributes added to the same entity with the same value kind. No similarity scoring and no stable ids in v1.
4. **Steps run inside the release transaction.** The order is: execute the steps, then plan (so the checks see the migrated data), then the participants' `release()`. If the plan still has errors, everything rolls back.
5. **Dry run = release and roll back.** "Check against data" runs step 4 and marks the transaction rollback-only. The report has exact per-step counts of affected and failed records, plus the number of records that break new rules.
6. **The checks become data-aware.** A check refuses only when some object holds data that does not fit, and says how many objects. For example, `attributeTypeChanged` checks whether the stored values fit the new type instead of whether any object exists. This alone unblocks many releases without a step.
7. **Only eager execution.** No lazy migration, no per-record release, no chaining across skipped releases. An organisation releases its own drafts one release at a time, and a starter update arrives as drafts through the same flow.
8. **No snapshot.** The transaction makes the migration all or nothing. An undo is a new release with the reverse steps.
9. **Storage.**
   - *Pending steps* are JSON rows owned by base-release: org, kind, key, op, parameters, and the resolved error id.
   - *At publish*, the steps are copied into the release record and become immutable. A correction is a new release.
   - *Steps that no longer fit.* A step that cannot run against the drafts as they stand, for example after a later draft edit removed its attribute, is kept but skipped, and reported as a warning (`release.step.skipped`) so the designer sees it and removes it.
10. **Execution in base-entity.** base-release orchestrates through the `MigrationParticipant` interface in processpuzzle-core, as it does releases through `ReleaseParticipant`. base-entity's `EntityMigrationParticipant` runs the three operations by rewriting each object's payload. Every written object's `version` moves on, so a concurrent edit fails on the optimistic lock instead of overwriting migrated data. A step that cannot change every object, such as a rename onto an attribute that already holds a value, fails (`release.step.failed`), which refuses the release. v1 rewrites object by object. Set-based JSONB SQL can replace that when volumes need it.
11. **Removed values are moved aside.** On release, the values of a removed attribute move to a reserved `_removed.<code>` key. Otherwise they would come back if the same code is added again later.
12. **Workflows stay refused.** The existing refusals stay: `workflowRemovedWhileRunning` and `taskRemovedWhileOpen`. `workflowInstancePolicy` is not offered until instances are pinned to a release.

### 12.4 Answers to the open questions (§11)

1. **Immutable after release.** Yes. A correction is a new release.
2. **Snapshot.** Not needed in v1 (decision 8).
3. **Storage.** base-release tables, as JSON (decision 9).
4. **Lazy migration.** Dropped (decision 7).
5. **Stable ids** (design view §11.1). Not introduced in v1. Ids or editor-recorded `renamedFrom` hints come later, if starter updates need renames without a designer.

### 12.5 Build order

1. **Data-aware checks.** The checks count the objects that do not fit (decision 6), and each problem names the attribute or state it is about (`subject`) and the number of objects (`affectedObjects`), which the Resolve forms build on. The existing pending-release view already is the dry run while there are no steps: the checks do not depend on `release()`.
2. **Steps end to end (backend).**
   - Pending steps in `org_migration_steps`, replaced as a whole through `PUT .../releases/pending/steps`.
   - `EntityMigrationParticipant` with the three operations.
   - The execution order of decision 4.
   - The dry run of decision 5 is the pending view itself. `GET .../releases/pending` runs the steps, plans, and rolls back, and reports each step's outcome.
   - Steps recorded with the release, and listed in its history.
   - A state is remapped with `mapValues` on the entity's state attribute. No state-specific operation is needed.
3. **Resolve forms and the step list** in the Release section: the rename question, the fill value, and the mapping table pre-filled with the distinct values found in the data.
4. **Moving removed values aside** (decision 11).

### 12.6 Later

- `changeType` and `deriveValue`, with a value-returning PPCL evaluation.
- Pinning workflow instances to the release they started under, using `ReleaseHistory.contentAt`. Then `workflowInstancePolicy`.
- `renameEntity`, `removeEntity`, `splitField`, `moveField`.
- Rename hints carried by starter updates.

## 13. Next documents

- `migration-operation-catalog.md`: operations, YAML shapes, v1 scope
- `migration-design-view.md`: diff algorithm, suggestion model, Design view flow, API sketch

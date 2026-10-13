# Diff-to-Suggestion Flow in the Design View

Status: draft, 2026-10-12
Part of: `definition-migrations-concept.md`, uses `migration-operation-catalog.md`

## 1. Goal

When the designer prepares a release, the Design view turns "what changed in my definitions" into "a ready migration set" with as few decisions as possible. The designer confirms, completes, and previews. They never write a migration from scratch.

## 2. Pipeline overview

```
 previous release          current draft
 (definition snapshot)     (definition snapshot)
          \                    /
           ▼                  ▼
         1. Definition diff (structural)
                   │
                   ▼
         2. Change classification
                   │
                   ▼
         3. Suggestion engine (rename detection, data inspection)
                   │
                   ▼
         4. Suggested steps (with status: auto / confirm / input needed)
                   │
        designer works through the list
                   ▼
         5. Validated migration set
                   │
                   ▼
         6. Dry run → report → release
```

## 3. Step 1: Definition diff

Compare the two definition snapshots **structurally**, per definition type, producing a list of atomic changes:

| Definition | Detected changes |
|---|---|
| Entity | entity added / removed, field added / removed, field type changed, required flag changed, option list changed, relation added / removed |
| State | state added / removed, transition added / removed |
| Workflow | task / artifact / role added / removed, ordering or gating changed |
| Rule | rule added / removed / changed (no data operation, validation only) |
| Document / App | UI-only, ignored for migration |

Each atomic change carries the definition key, path (`Customer.fields.customerName`), old value, and new value.

Identity matters: definitions are matched by **stable ids** (a generated id per entity, field, and state that survives edits), not by names. A rename in the Design view therefore appears in the diff as a *name change on the same id*, which makes rename detection certain whenever the designer used the rename action in the editor.

The heuristics below are only needed for changes where identity was lost, for example after an import or when a field was deleted and recreated.

## 4. Step 2: Classification

Each atomic change is classified:

| Class | Meaning | Designer effort |
|---|---|---|
| **Auto** | Unambiguous, no input needed | none, just visible in the list |
| **Confirm** | Platform has a likely interpretation | one click |
| **Input** | Platform needs a value or mapping | small form |
| **Block** | Release cannot proceed | must resolve |
| **None** | No migration needed | not shown, summarised |

Examples:

| Change | Class | Suggested step |
|---|---|---|
| Field renamed (same id) | Auto | `renameField` |
| Optional field added | None | (nothing) |
| Required field added | Input | `addField` + default |
| Field removed | Confirm | `removeField` (archive) |
| Field removed + similar field added | Confirm | `renameField` (rename suggestion) |
| Field type changed | Input | `changeType` + conversion options |
| Option list changed | Input | `mapValues` |
| Entity renamed (same id) | Auto | `renameEntity` |
| State renamed (same id) | Auto | `renameState` |
| State removed, records exist | Input / Block | `remapState` (Block until a mapping is given) |
| Field becomes required, empty values exist | Input | `setDefault` or `deriveValue` |
| Workflow definition changed, instances running | Confirm | `workflowInstancePolicy` (default pre-selected) |
| New rule | None (validation report) | none |

## 5. Step 3: Suggestion engine

### 5.1 Rename detection (when ids don't match)

For every removed field `R` and added field `A` in the same entity, compute a score from 0 to 1:

| Signal | Weight |
|---|---|
| Same data type | 0.30 |
| Name similarity (normalised, Levenshtein / token overlap) | 0.30 |
| Same position among siblings | 0.10 |
| Same constraints (required, length, format) | 0.15 |
| Value overlap in real data (sample of values matches the new type and, for enums, the new options) | 0.15 |

Thresholds:

- score ≥ 0.80: suggested as rename, class *Confirm* (pre-selected)
- 0.50 – 0.79: shown as "Is this the same field?" with alternatives, class *Confirm* (not pre-selected)
- below 0.50: treated as separate remove + add

The assignment is solved as a best match (no field is the target of two renames). Entity and state rename detection use the same approach with the signals adapted (field set overlap for entities, transition overlap for states).

### 5.2 Data inspection

Because the migration targets real data, the engine reads **statistics** from the organisation's data (never the full records) to make suggestions better:

- distinct values per changed field (for `mapValues`, `remapState`)
- count of records per state (to decide Block vs. Input vs. None for removed states)
- count of empty values for newly required fields
- convertibility rate for type changes ("98 % of values parse as numbers")
- running workflow instance counts per workflow

For a **starter update** (no specific customer), data inspection is skipped and the steps are generated generically. They are completed per customer at dry run time.

### 5.3 Suggestion model

```yaml
suggestion:
  id: sg-12
  changeRefs: [ch-7, ch-9]            # atomic changes it explains
  class: confirm                       # auto | confirm | input | block
  step:                                # proposed migration step
    op: renameField
    entity: Customer
    from: customerName
    to: name
  confidence: 0.86
  alternatives:
    - step: { op: removeField, entity: Customer, field: customerName, mode: archive }
      label: "Treat as removed, and name as a new field"
  evidence:
    - "Same type (text)"
    - "Name similarity 0.74"
    - "100 % of existing values fit the new constraints"
  needs:                               # populated for class = input
    - key: default
      type: value
      label: "Value for existing records"
```

## 6. Step 4: The Design view

### 6.1 Entry point

The Design view gets a **Release** action. It opens the *Release wizard* with three tabs: **Changes**, **Migration**, **Preview**. A release cannot be published until the migration is complete and a dry run has succeeded.

### 6.2 Changes tab

Read-only overview of the diff, grouped by definition, with counts:

```
 Release 1.3.0 → 1.4.0
 ┌──────────────────────────────────────────────┐
 │ Entities     3 changes                       │
 │   Customer   field renamed: customerName → name │
 │   Customer   field added: segment (required) │
 │   Order      field type changed: quantity    │
 │ States       1 change                        │
 │   OrderLifecycle  state removed: Pending     │
 │ Workflows    1 change (2 instances running)  │
 │ Rules        2 changes                       │
 └──────────────────────────────────────────────┘
```

Purpose: the designer verifies that this is what they intended before touching the migration.

### 6.3 Migration tab

A to-do style list of suggestions, one card per suggestion, sorted: *Block*, *Input*, *Confirm*, *Auto*. A progress bar shows "5 of 7 steps ready".

```
 ● Needs your input (2)
 ┌──────────────────────────────────────────────┐
 │ Customer.segment is new and required         │
 │ What value should existing customers get?    │
 │  (•) Fixed value  [ standard        ]        │
 │  ( ) Calculated   [ PPCL expression ]        │
 │                                  [ Apply ]   │
 └──────────────────────────────────────────────┘
 ┌──────────────────────────────────────────────┐
 │ State "Pending" was removed, 34 orders in it │
 │ Where should these orders go?                │
 │   Pending  →  [ Submitted ▾ ]                │
 │                                  [ Apply ]   │
 └──────────────────────────────────────────────┘

 ○ Please confirm (2)
 ┌──────────────────────────────────────────────┐
 │ Is "name" the new "customerName"?            │
 │ Same type, 100 % of values fit.              │
 │   [ Yes, rename ]   [ No, they are different ] │
 └──────────────────────────────────────────────┘

 ✓ Done automatically (3)   [ show ]
```

Rules for the cards:

- Each card uses designer language, not operation names. The technical step is available under "Show details" (read-only YAML).
- *Confirm* cards show the evidence in one line and the alternative as a secondary button.
- *Input* cards use the form for the operation (value, PPCL editor with autocomplete, mapping table, conversion options).
- For `mapValues` and `remapState`, the mapping table is **pre-filled** with the distinct old values found in the data.
- The designer can add steps manually through **Add step**, which offers the catalog operations as plain-language actions ("Combine fields", "Split a field", "Fill empty values").
- Steps can be reordered by drag and drop. Invalid orders are rejected immediately with a message.
- Every card can be marked **Skip step** with a required note, for the rare case where the designer knowingly does not migrate. This is recorded in the release report.

### 6.4 Preview tab

Runs the dry run against a chosen organisation's data (or the Testbed data when no organisation is chosen).

```
 Dry run on: acme            [ Run again ]
 Step                    Affected   Failed
 renameField             1,240      0
 addField segment        1,240      0
 changeType quantity       980      3   [ show failures ]
 remapState Pending         34      0
 Rule violations: CustomerNameRequired 17 records
 [ Show before / after samples ]
```

- **Failures** link to the failing record and offer the fixes: change the failure policy (`skip`, `useDefault`), adjust the conversion, or edit the step.
- **Rule violations** from new rules are listed separately, not blocking, but with a clear count.
- The release button is enabled when the migration is complete, no *Block* items remain, and the latest dry run has no unhandled failures.

### 6.5 After release

A **Release history** panel in the Design view shows each release with its migration set, its execution report per organisation, and, for reversible sets, a **Restore snapshot** action.

## 7. Backend contract (OpenAPI-first sketch)

Resources, owned by a new `base-release` feature library (Spring Modulith module, hexagonal):

```
POST   /releases                         create release candidate from draft
GET    /releases/{id}                    status, version, diff summary
GET    /releases/{id}/diff               atomic changes
GET    /releases/{id}/suggestions        suggested steps (class, evidence, needs)
PUT    /releases/{id}/migration          replace migration set (validated)
PATCH  /releases/{id}/migration/steps/{stepId}   edit single step
POST   /releases/{id}/dry-run            run against organisation, returns run id
GET    /releases/{id}/dry-run/{runId}    report
POST   /releases/{id}/publish            publish (guards: complete + successful dry run)
POST   /releases/{id}/apply              execute for an organisation (eager) 
GET    /releases/{id}/runs               execution history and reports
```

Module responsibilities:

| Module / library | Responsibility |
|---|---|
| base-entity-backend | Executes field / entity operations on the EAV/JSONB layer, exposes statistics (distinct values, empties, parse rates) |
| base-state-backend | Executes `renameState` / `remapState`, exposes record counts per state, suppresses events during migration |
| base-workflow-backend | Exposes running instance counts, applies `workflowInstancePolicy` |
| PPCL engine | Parses and type-checks expressions for `where`, `value`, and mapping rules |
| base-release (new) | Diff, classification, suggestion engine, migration set storage, dry run orchestration, reports |

Cross-module integration stays on Spring Modulith application events and public module APIs, consistent with the existing architecture decisions.

## 8. Frontend structure (Angular, signals-first)

- `ReleaseWizardComponent`: stepper with the three tabs
- `ChangeSummaryComponent`: diff overview (read-only)
- `MigrationTaskListComponent`: sorted suggestion cards, progress signal derived from the card states
- Operation card components, one per operation form, registered through an injection token (same pattern as `WIDGET_REGISTRY`), so new operations add a card without touching the list: `MIGRATION_STEP_CARD_REGISTRY`
- `MappingTableComponent`: reusable for `mapValues` and `remapState`
- `DryRunReportComponent`: step table, failures, samples
- State held in a signal store per release (`suggestions`, `steps`, `dryRunReport`, `completeness` as computed signals)

## 9. Edge cases

- **Two releases in one draft**: the diff always compares against the *last published* release, so changes accumulate until publish.
- **Draft changed after dry run**: the dry run is marked stale, the release button disables, and the migration tab recomputes suggestions while keeping the designer's confirmed decisions where the underlying change is unchanged.
- **Designer reverts a change**: the matching suggestion disappears, and its decision is dropped.
- **Customer skipped a release**: the platform chains the migration sets and runs one combined dry run.
- **Starter updates**: suggestions are generated without data inspection. Remaining *Input* items are completed at the customer's dry run, or skipped with a documented default.

## 10. Build order proposal

1. Definition snapshots with stable ids, and the structural diff (no UI)
2. Classification for the *Must* tier, with *Auto* class only (renames by id, additive fields, state rename)
3. Migration set storage and the executor for the *Must* operations, with dry run report
4. Release wizard: Changes + Migration + Preview, with *Auto* and *Input* cards
5. *Confirm* cards with the rename heuristics and data inspection
6. *Should* tier operations (`changeType`, `mapValues`, `deriveValue`) with their cards
7. Release history, snapshot restore, starter update flow

## 11. Open points

1. Stable ids: are they already present in Entity, State, and Workflow definitions, or must they be introduced with this feature?
2. Thresholds for rename confidence need tuning on real definition changes (the SailRC migration is a good test set).
3. Whether dry runs against production data are allowed for the designer role, or only against a snapshot copy.
4. How snapshots interact with the daily reset of the Testbed.

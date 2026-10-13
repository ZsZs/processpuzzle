# Migration Operation Catalog

Status: draft, 2026-10-12
Part of: `definition-migrations-concept.md`

## 1. Migration document

A migration set is one YAML document per release step. It is stored with the release bundle.

```yaml
migration:
  from: 1.3.0
  to: 1.4.0
  organisation: null          # null = applies to every organisation on this release
  steps:
    - id: s1
      op: renameField
      entity: Customer
      from: customerName
      to: name
    - id: s2
      op: addField
      entity: Customer
      field: segment
      default: "standard"
```

### Common step properties

| Property | Type | Meaning |
|---|---|---|
| `id` | string | Stable id within the migration, used in reports and ordering |
| `op` | enum | Operation name from the catalog |
| `where` | PPCL expression, optional | Restricts the step to matching records |
| `onError` | `fail` / `skip` / `useDefault` | Failure policy per record. Default `fail` |
| `emitEvents` | boolean | Whether state machine / domain events fire. Default `false` |
| `note` | string, optional | Designer comment, shown in reports |
| `reversible` | derived | Set by the platform from the operation and its parameters |

Steps run **in the listed order**. The Design view can reorder them, and the platform validates dependencies (for example, `renameField` must come before a step that uses the new name).

## 2. Operations

Each operation has: a name in designer language, parameters, behaviour, reversibility, and whether the diff can detect it automatically.

### 2.1 addField

Adds a field to existing records.

```yaml
- op: addField
  entity: Customer
  field: segment
  default: "standard"          # literal ...
  # value: "if(revenue > 10000, 'key', 'standard')"   # ... or PPCL expression
```

- Behaviour: sets the value on all existing records of the entity (respecting `where`).
- If the field is required and neither `default` nor `value` is given, the release is blocked.
- Reversible: yes (drop the field).
- Auto-detected: yes. Default is requested from the designer if the field is required.

### 2.2 removeField

```yaml
- op: removeField
  entity: Customer
  field: fax
  mode: archive                # archive | drop
```

- `archive`: values move to an archive area of the record (kept, invisible to rules and UI).
- `drop`: values are deleted.
- Reversible: yes for `archive`, no for `drop`.
- Auto-detected: yes. The designer chooses the mode, default `archive`.

### 2.3 renameField

```yaml
- op: renameField
  entity: Customer
  from: customerName
  to: name
```

- Behaviour: moves the value to the new key. Definitions, rules, and document templates referencing the field are updated by the same release, not by the migration.
- Reversible: yes.
- Auto-detected: as a *suggestion*, by rename heuristics (see the Design view document). Needs designer confirmation.

### 2.4 changeType

```yaml
- op: changeType
  entity: Order
  field: quantity
  from: text
  to: number
  conversion:
    onInvalid: useDefault      # fail | skip | useDefault
    default: 0
    # format: "dd.MM.yyyy"     # for text <-> date
```

- Supported conversions in v1: text ↔ number, text ↔ date, number ↔ boolean, anything → text.
- Reversible: only for lossless conversions (anything → text is reversible, text → number is not).
- Auto-detected: yes. The designer completes the conversion options.

### 2.5 mapValues

Maps old values to new ones. Used for enums, codes, and lookup values.

```yaml
- op: mapValues
  entity: Customer
  field: country
  mapping:
    DE: Germany
    HU: Hungary
    AT: Austria
  unmapped: keep               # keep | fail | useDefault
  default: Other
```

- The Design view shows this as a two-column table with all distinct old values found in the data pre-filled on the left.
- Reversible: yes when the mapping is one-to-one.
- Auto-detected: yes when an enum or option list changes. The designer fills the right column.

### 2.6 setDefault

Fills empty values on existing records.

```yaml
- op: setDefault
  entity: Invoice
  field: currency
  value: "EUR"
  only: empty                  # empty | always
```

- Reversible: no (original empties cannot be distinguished afterwards).
- Auto-detected: partially. When a field becomes required, it suggests this step.

### 2.7 deriveValue

Sets or overwrites a field from a PPCL expression over the same record.

```yaml
- op: deriveValue
  entity: Customer
  field: displayName
  value: "concat(firstName, ' ', lastName)"
  only: empty
```

- Covers combine fields, calculated fields, and many "split" cases.
- Reversible: no.
- Auto-detected: no. Designer-initiated.

### 2.8 splitField

Splits one field into several by a delimiter or pattern.

```yaml
- op: splitField
  entity: Customer
  field: fullName
  into:
    - firstName
    - lastName
  by: " "
  limit: 2
  keepSource: false
```

- Designer sees a small test box: "Max Mustermann → Max | Mustermann" on real sample values.
- Reversible: when `keepSource: true`.
- Auto-detected: no. Designer-initiated, sometimes suggested when one field disappears and several appear.

### 2.9 moveField

Moves a field from one entity to another via a relation.

```yaml
- op: moveField
  from:
    entity: Order
    field: customerAddress
  to:
    entity: Customer
    field: address
  via: Order.customer          # the relation that links source records to target records
  conflict: keepTarget         # keepTarget | overwrite | fail
```

- Reversible: no.
- Auto-detected: as a suggestion, when a field disappears in one entity and appears in a related one.

### 2.10 renameEntity

```yaml
- op: renameEntity
  from: Client
  to: Customer
```

- Behaviour: updates the entity key of existing records, and the references stored in relations, states, workflow artifacts, and documents.
- Reversible: yes.
- Auto-detected: as a suggestion (same fields, different name).

### 2.11 removeEntity

```yaml
- op: removeEntity
  entity: LegacyContact
  mode: archive                # archive | drop
```

- Blocked if other definitions still reference it.
- Reversible: yes for `archive`.
- Auto-detected: yes.

### 2.12 renameState

```yaml
- op: renameState
  stateMachine: OrderLifecycle
  from: Open
  to: Submitted
```

- Reversible: yes.
- Auto-detected: as a suggestion.

### 2.13 remapState

Moves records from one state to another. Used when a state is removed, split, or merged.

```yaml
- op: remapState
  stateMachine: OrderLifecycle
  mapping:
    Pending: Submitted
    Approved: Approved
    OnHold:                       # split: decided by a rule
      - when: "holdReason == 'payment'"
        to: WaitingForPayment
      - otherwise: Submitted
  emitEvents: false
```

- Behaviour: changes the state of matching records without firing transitions.
- Blocked at release time if any state in the old definition that still holds records has no mapping and no longer exists.
- Reversible: yes for one-to-one mappings.
- Auto-detected: yes (removed state with records in it). The designer provides the mapping.

### 2.14 workflowInstancePolicy

Declares what happens to in-flight workflow instances. It is not a data operation, but a policy attached to the migration.

```yaml
- op: workflowInstancePolicy
  workflow: OrderApproval
  inFlight: finishOnOldDefinition   # finishOnOldDefinition | remap
  # remap:
  #   taskMapping: { ReviewOrder: ReviewOrderV2 }
```

- Default is `finishOnOldDefinition`. `remap` is advanced, flagged with a warning, and in v1 limited to one-to-one task mappings.
- Auto-detected: yes (changed workflow definition with running instances). The default is pre-selected.

### 2.15 No data operation required

These definition changes do **not** produce steps:

- new or changed rules (PPCL) — existing data is validated lazily, and a release report lists records that violate new rules
- new entities or states
- new workflows
- new optional fields without a default (use `addField` only if a value is wanted)
- UI-only changes (layout, labels, App definition)

For new rules, the dry run reports "N records would violate Rule X". The designer decides whether to fix data (with `setDefault` or `deriveValue`) or relax the rule.

## 3. Scope for v1

| Tier | Operations |
|---|---|
| **Must** | addField, removeField, renameField, renameEntity, renameState, remapState, setDefault, workflowInstancePolicy (default only) |
| **Should** | changeType, mapValues, deriveValue |
| **Could** | splitField, moveField, removeEntity |
| **Later** | custom step, workflow remap, cross-entity merge |

The *Must* tier covers the typical evolution of an application (additive changes, renames, and state cleanup) and is entirely auto-detectable.

## 4. Validation rules

- All referenced entities, fields, and states must exist in the `from` or `to` definition at that point of the step order.
- Required fields in the new definition must be satisfied for all records after the full migration, or the release is blocked.
- PPCL expressions are parsed at authoring time and type-checked against the entity definition.
- A `drop` mode shows a confirmation with the number of affected records in the dry run.
- Circular `moveField` or rename chains are rejected.
- Steps that would fire events (`emitEvents: true`) are shown with a warning.

## 5. Report structure (dry run and execution)

```yaml
report:
  migration: 1.3.0 -> 1.4.0
  organisation: acme
  mode: dryRun
  steps:
    - id: s1
      op: renameField
      affected: 1240
      failed: 0
      samples:
        - recordId: c-1021
          before: { customerName: "Acme GmbH" }
          after:  { name: "Acme GmbH" }
    - id: s4
      op: changeType
      affected: 980
      failed: 3
      failures:
        - recordId: o-554
          reason: "'12,5 kg' is not a number"
  ruleViolations:
    - rule: CustomerNameRequired
      records: 17
```

## 6. Open points

1. Naming of operations in the UI language: keep the technical key (`remapState`) in YAML, use plain labels in the Design view ("Move records to another state").
2. Archive area format for removed fields: a reserved `_archive` key per record, or a separate table.
3. Whether `where` filters should be limited to a PPCL subset (no side effects, no cross-entity lookups) for predictable performance.

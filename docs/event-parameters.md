# Event parameters — what an event carries, declared once

Status: **accepted** (2026-10-16). Phases 1 and 2 are specified in detail; phase 3 in outline.

## Decisions (2026-10-16)

1. **Parameters are namespaced by the event that carried them** in the context: `<event>.<name>`, where
   `<event>` is the id of the start event or the event use — `order-created.orderNumber`,
   `invoice-issued.invoiceNumber`. Two events carrying an `order` never overwrite each other.
2. **No re-binding.** Once an artifact is bound, a later event naming a different record does not change it.
3. **A document's state is a `currentState` too.** base-document publishes state facts for its documents.
4. **Declared SYSTEM parameters are checked against the entity's attributes** when the event definition is
   saved.

## Why

A workflow run does not know which records it is about. Three symptoms, all visible on the task dashboard:

- **Reads/Writes link to the catalog, not the record.** `StartWorkflowInstanceUseCase` creates one
  `ArtifactInstance` per declared artifact but never sets its `entityId`. The order that started the run is
  known — it is the instance's `entityId` — but no artifact is bound to it, so the dashboard has nothing to
  link to and falls back to the Artifact Definition.
- **`ArtifactInstance.currentState` is never set.** The contract says it is "refreshed when
  EntityStateChangedEvent is received"; no such listener exists. Since PR #932 the dashboard enables
  *Complete Task* only when every output is in its required state, so on stage it stays disabled for every
  task whose workflow names an output state.
- **What an event carries is restated per use.** `OrderCreatedEvent`'s order number is read with
  `orderNumber: $.payload.orderNumber` in the start event of every workflow it starts, and `InvoiceIssued`'s
  invoice number in every catch of it. Nothing says what an event is *supposed* to carry, so nothing can
  check that a thrower supplies it.

## What exists and stays

| Piece | Where | Role after this change |
| --- | --- | --- |
| Instance `context` | `WorkflowInstance.context`: `initialContext` + every task's and catch's `contextContribution` | Still the one map tasks, steps and rules read. Event parameters land in it. |
| `payloadMapping` | `StartEvent`, `EventUse` (CATCH: event → context; THROW: context → payload) | Kept as the explicit override; phase 3 makes most of them unnecessary. |
| JSONPath | the `payloadMapping` values, evaluated against the whole event (`$.subjectId`, `$.payload.x`) | The expression language for reading an event or the context. |
| PPCL | base-rule; step `inputMapping` | The expression language where a value has to be *computed*. |
| `entityLabel` | `WorkflowInstance.entityLabel`, via `EntityLabelPort` | Extended to artifact instances. |
| `PlatformEvent` / `DefinedEventOccurred` | api-contracts `shared.event` | Unchanged; parameters are resolved from them. |

**No new DSL.** Filling is by **name and type** by default; an expression — JSONPath to read, PPCL to
compute — is only an override. A third expression language would be one more thing to learn, document and
validate, for cases the two above already cover.

## The concept

### Parameters on an event definition

An `EventDefinition` declares the values an occurrence of it carries:

```yaml
- id: InvoiceIssued
  kind: MESSAGE
  parameters:
    - { name: order,         type: ENTITY_REF, entityType: order,    required: true }
    - { name: invoice,       type: ENTITY_REF, entityType: document, required: true }
    - { name: invoiceNumber, type: STRING,                           required: true }
```

`EventParameter`: `name` (an identifier, unique within the definition), `type` (`STRING`, `NUMBER`,
`BOOLEAN`, `DATE`, `ENTITY_REF`), `entityType` (the base-entity definition code; `ENTITY_REF` only),
`required`, `description`.

### How an occurrence is filled

- **SYSTEM** events are platform facts, so they need no mapping at all:
  - an implicit parameter **`subject`**, `ENTITY_REF` of the definition's `subjectType`, is always present
    and is the fact's `subjectId` — it does not have to be declared;
  - a declared parameter takes the payload attribute of the same name (`orderNumber` ← `payload.orderNumber`).
- **MESSAGE / SIGNAL** events thrown by a workflow take each parameter from the context by name: a plain
  variable of that name first (a `payloadMapping` target, a step's `outputMapping` target, an object name),
  otherwise the one namespaced variable `<event>.<name>` carrying it. When several events carry it, the name
  is ambiguous and the THROW's `payloadMapping` has to say which. `payloadMapping` also overrides a name, or
  computes a value.
- An occurrence's resolved parameters are stored on its **`EventInstance.parameters`** (new, read-only) —
  the event stream becomes the record of what each step was about — and land in the context **namespaced by
  the event** (decision 1): a nested map under the start event's or event use's id, so
  `order-created.orderNumber` in prose and `$['order-created'].orderNumber` in JSONPath. Existing
  `payloadMapping` targets stay plain keys, as today.
- Declared SYSTEM parameters must name an attribute of the subject's entity definition; the event
  definition's save refuses one that does not (decision 4), so renaming an attribute cannot silently unfill a
  parameter.

### Binding objects to the run

An `ENTITY_REF` parameter of an event the run **starts on or catches** binds the run's artifact of that
entity type:

1. The artifact is the workflow's `ArtifactUse` whose `ArtifactDefinition` is `ENTITY` with
   `artifactTypeId == entityType` (`DOCUMENT` for `entityType: document`).
2. If the workflow declares more than one such artifact, the binding is ambiguous and the `ArtifactUse` names
   its source explicitly: `bindFrom: <startEventId | eventUseId>.<parameter>`. Save refuses an ambiguous
   binding that names none.
3. Binding sets `ArtifactInstance.entityId`, and the record's id is in the context twice: namespaced with the
   other parameters (`order-created.subject`), and under the artifact's `objectName` as a plain variable
   (`new_order → <order id>`) — the workflow's own name for the object, which is what tasks and rules address.
4. **No re-binding** (decision 2): an artifact bound once keeps its record. A later event naming a different
   record of that type is recorded on its event instance and does not change the binding.
5. A task completion that contributes a context key equal to an *unbound* artifact's `objectName` binds it
   too — the path for an object a person creates, e.g. a document attached by hand.

### Keeping the state current

A listener on raw `PlatformEvent` `STATE_CHANGED` facts updates `currentState` of every artifact instance
bound to `(subjectType, subjectId)` in a running instance. On binding, the current state is read once through
the existing `EntityStateGateway.currentState`. This is what makes the dashboard's Complete gate and the chips'
state honest.

**Documents** (decision 3). base-document publishes no platform facts today, and a document's status is per
translation and derived (`DocumentStatus`: `DRAFT`, `PUBLISHED`, `PUBLISHED_WITH_DRAFT_CHANGES`). It will
publish `PlatformEvent.stateChanged(orgKey, "document", documentId, status, …)` whenever the status of the
document's **default-language translation** changes — on publish, unpublish, and the first edit after a
publish — so a bound `DOCUMENT` artifact's `currentState` follows the same listener as an entity's, and a
workflow can require an invoice to be `PUBLISHED` before a task completes.

### Naming the bound records

`ArtifactInstance.entityLabel` (new, read-only), resolved per response through the existing `EntityLabelPort`
exactly as `WorkflowInstance.entityLabel` is. The dashboard then needs no client-side name lookup:
`ObjectNameResolver` (PR #934) is replaced by these labels.

## Phase 1 — parameters, automatic SYSTEM filling, binding, live state

**Contract**

| Schema | Change |
| --- | --- |
| `base-event-api` `EventDefinitionInput` | `parameters: EventParameter[]` (optional) |
| `base-event-api` `EventParameter` | new: `name`, `type`, `entityType`, `required`, `description` |
| `base-workflow-api` `EventInstance` | `parameters` (readOnly, `additionalProperties`) |
| `base-workflow-api` `ArtifactInstance` | `entityLabel` (readOnly); `entityId` documented as bound by the run |
| `base-workflow-api` `ArtifactUse` | `bindFrom` (optional) |
| `shared.event.DefinedEventOccurred` | `parameters` — resolved by base-event when it republishes a match |

**Backend**

- base-event: persist `parameters` on the definition (JSONB, like the other embedded lists); validate names
  and types on save; resolve SYSTEM occurrences' parameters when `PlatformEventCatalogListener` republishes,
  and carry them on `DefinedEventOccurred`.
- base-workflow:
  - start (`TRIGGERING_EVENT`) and catch (`CatchOccurrence`): bind artifacts from the occurrence's
    `ENTITY_REF` parameters, record `EventInstance.parameters`, merge parameters into the context;
  - explicit and `INPUT_ARTIFACT` starts bind the subject to its artifact the same way — the subject *is* an
    `ENTITY_REF`;
  - new `ArtifactStateListener` (raw `PlatformEvent` `STATE_CHANGED`) refreshes `currentState`;
  - `WorkflowExecutionMapper` fills `ArtifactInstance.entityLabel`;
  - save-time validation of `bindFrom` and of ambiguous bindings.
- base-event: the definition's save checks declared SYSTEM parameters against the subject entity's attributes,
  through a port the host application supplies from base-entity (as base-workflow's `EventCatalogPort` is
  supplied from base-event).
- base-document: publish `STATE_CHANGED` facts for the default-language translation's status, and `CREATED` /
  `DELETED` for documents, through the shared `PlatformEvent`.
- The definition release (`definition-release.md`) treats `parameters` as part of an event definition's
  content, so a changed parameter list is a pending change.

**Frontend**

- base-event: the Event Definition form gets a Parameters list (an embedded entity, like a workflow's start
  events).
- base-workflow dashboard: chips and diagram use `ArtifactInstance.entityId` / `entityLabel`; links go to the
  record through the existing `DashboardLinkResolver`; the Process view lists the run's context variables;
  `ObjectNameResolver` is removed.

**Seeds** — `OrderCreatedEvent` declares `orderNumber`, `customerName`; `InvoiceRequested` declares `order`,
`orderNumber`, `customerName`; `InvoiceIssued` declares `order`, `invoice`, `invoiceNumber`. The workflows'
existing `payloadMapping`s keep working — their plain-key targets are what the seeded tasks read — and become
redundant once phase 3 lets those readers use the namespaced values.

**Compatibility** — a definition without parameters behaves exactly as today, except that every SYSTEM event
now binds its subject. Existing instances keep unbound artifacts; nothing is back-filled.

## Phase 2 — checked parameters

**Release** (`ThrowParameterCoverage`, read by `WorkflowReleaseParticipant` against the drafts of the event
catalog and the task definitions). A required parameter of a thrown event is filled when

- the THROW's `payloadMapping` names it; or
- a plain variable of that name is written **upstream** — a start event's or an upstream catch's `payloadMapping`
  target, an upstream task's step `outputMapping` target, or an artifact's `objectName`; or
- exactly one carrier upstream declares a parameter of that name: the start events taken together (a run begins on
  one of them), and each upstream catch. A SYSTEM event carries its `subject` without declaring it.

Upstream is what the THROW depends on, transitively; a boundary event depends on its task. A join of `ANY` is read
as if every branch ran — the check refuses what no path could fill, not what one path might miss. Otherwise:

| Case | Severity | `errorId` |
| --- | --- | --- |
| Nothing upstream produces the name | error | `workflow.release.unfilledThrowParameter` |
| Several carriers, no plain variable | error | `workflow.release.ambiguousThrowParameter` |
| Only an upstream task's completion could supply it | warning | `workflow.release.throwParameterFromTaskOnly` |

Each problem's `subject` is the THROW's event use id. `EventCatalogPort.draftParametersOf` supplies the draft
parameters; an event the catalog cannot tell parameters of is not checked.

**Runtime.** A due THROW whose required parameter resolves to nothing is **held**: it stays `PENDING`, records why
in the new, read-only `EventInstance.blockedReason`, publishes nothing, and its dependents wait. Every advance of
the run tries it again; once the parameter resolves it is thrown and the reason cleared. A new status was not
added: Hibernate keeps an enum column's check constraint under `ddl-auto: update`, so a new value would be refused
by existing databases — and `PENDING` already means "not sent yet, retried". The dashboard shows a held throw as a
chip on the run's board, with the reason on hover; the Event Instance form shows the reason and the parameters.

Phase 2 also wires `EventCatalogPort.releasedParametersOf` in the testbed's `EventCatalogAdapter`: phase 1
declared it, but the adapter did not implement it, so a deployed throw filled no parameters.

## Phase 3 — mappings by convention (outline)

- CATCH and TRIGGERING_EVENT start events no longer need a `payloadMapping` for parameters of the same name;
  the modeler's forms show the event's parameters and the mapping only as overrides.
- Seed `payloadMapping`s that merely restate parameters are removed.

## Open questions

- **Which translation is a document's state** (decision 3) is settled as the default language's above; a
  workflow per language would need the state per translation, which is not in scope.

# ProcessPuzzle Workflow Modeling — Three-Layer Pattern

This document summarizes the SPEM-inspired modeling pattern used across
ProcessPuzzle's workflow engine (`base-workflow-backend`) for Tasks,
Artifacts, Roles, and Tools.

## The Core Pattern

Every major concept in the workflow model is split into three layers,
mirroring SPEM 2.0's separation of **Method Content** from **Process**:

| Layer | Scope | Purpose |
|---|---|---|
| **Definition** (Method Content) | Global, reusable | Abstract, context-free knowledge — "what this concept is" |
| **Use** (Process) | Workflow-scoped | Binds a Definition into a specific workflow, with workflow-specific configuration |
| **Instance / Runtime** | Workflow-instance-scoped | The concrete, running thing at execution time |

Rationale: a Definition can be reused across many workflows without
duplication. A Use lets each workflow configure that reusable concept
differently (different bindings, constraints, or sequencing) without
mutating the shared Definition. Runtime tracks actual execution state.

---

## Task

- **TaskDefinition** — the goal, informal steps, and abstract input/output
  ArtifactDefinition slots. Reusable across workflows.
- **TaskUse** — a TaskDefinition placed into a specific Workflow. Carries
  workflow-scoped config: bound ArtifactUse references (via
  `TaskIOReference`), assigned RoleUse (performer), sequencing/position in
  the workflow graph, guards/preconditions, optionality overrides.
- **TaskInstance** — the runtime execution of a TaskUse within a running
  WorkflowInstance. Completion fires state machine events (SPEM pattern);
  gates workflow progression based on artifact state.

## Artifact

- **ArtifactDefinition** — abstraction over Entity, Document, or Widget.
  Reusable, context-free.
- **ArtifactUse** — **workflow-scoped** (not task-scoped), so the same
  artifact occurrence can flow across multiple TaskUses in the same
  workflow (e.g. produced by one task, consumed by another). Carries
  expected entry/exit state (gates workflow progression), mandatory/
  optional flag, and the concrete Entity/Document/Widget subtype binding.
- **ArtifactInstance** — the actual Entity/Document/Widget row at runtime
  (lives in `base-entity-backend`'s EAV/JSONB storage).
- `TaskIOReference` points at an **ArtifactUse** (not directly at
  ArtifactDefinition), with a `ReferenceType` discriminator
  (INPUT/OUTPUT). Multiple TaskIOReferences from different TaskUses can
  point at the same ArtifactUse, forming a proper artifact-flow graph
  through the workflow.

## Role

- **RoleDefinition** — abstract performer role (e.g. "Reviewer",
  "Approver"). No notion of *who*.
- **RoleUse** — workflow-scoped binding of a RoleDefinition into a
  specific workflow; this is what TaskUse actually references as its
  performer (not RoleDefinition directly). Can narrow eligibility
  constraints per workflow. Multiple TaskUses in the same workflow can
  share one RoleUse (e.g. the same Reviewer occurrence handles both
  initial review and final sign-off).
- **Assignment** (Runtime) — rather than a "RoleInstance," runtime
  resolution is a binding of RoleUse → concrete User (or candidate pool,
  for claim-based assignment). Recommended: resolve assignment at the
  **TaskInstance** level (referencing RoleUse for eligibility rules)
  rather than baking a User into RoleUse itself — this supports
  round-robin/load-balanced assignment when the same RoleUse backs
  multiple task occurrences.

### Task ↔ Role cardinality (Method Content)

`TaskUse` ↔ `RoleUse` is **many-to-many**
(`performedByRoleDefinitions: Set<RoleDefinition>`), mirroring SPEM's
`ProcessPerformer` association. A TaskUse selects one (or a constrained
subset) of the candidate RoleDefinitions and binds it via RoleUse.
Primary/secondary performer distinction is resolved at the TaskUse level,
keeping Method Content a flat, unordered candidate set.

## Tool (external service integration)

- **ToolDefinition** — abstract capability (e.g. "Email Service",
  "External Data Provider"): interface only — required input, produced
  output, category. No connection details.
- **ToolUse** — workflow-scoped binding: which TaskUse triggers it, on
  what lifecycle event (task started/completed, artifact state
  transition), concrete endpoint/template/mapping, and how its
  input/output map onto the TaskIOReference/ArtifactUse graph.
- **ToolInvocation** (Runtime) — one record per actual call: timestamp,
  request/response payload, success/failure, retry count. An
  execution-history/audit entry rather than a stateful instance.

### Orthogonal: ToolConfiguration

Credentials and connection config (SMTP settings, API keys) are
**organisation-scoped**, not workflow-scoped — they don't belong on
ToolUse (per-workflow) or ToolDefinition (shared Method Content across
tenants). Introduce a separate `ToolConfiguration`, scoped per
`/organisations/{orgKey}` like the rest of the multi-tenancy model, which
ToolUse references.

**Trigger wiring** for ToolUse should follow the existing event-driven
convention: ToolUse subscribes to TaskUse/ArtifactUse lifecycle events,
consistent with how base-workflow already couples task completion to
base-state transitions via event listeners — rather than being polled or
invoked imperatively.

## Start and end events

A workflow says how an instance comes into being with a list of
**StartEvents** — BPMN's start event, carrying one of SPEM's four start
mechanisms:

| `startType` | SPEM equivalent | Admits an explicit start (`POST /instances`) when |
|---|---|---|
| `ROLE_DEFINITION` | Role Definition + Task Use | the caller holds one of `authorizedRoles` (none named = anyone) |
| `INPUT_ARTIFACT` | Artifact / Precondition | `entityId` is given and each required ENTITY artifact is in its required state |
| `TRIGGERING_EVENT` | Triggering Event | never — the event starts the workflow |
| `TIME_BASED_PRECONDITION` | Time-based Precondition / Milestone Guard | never — the milestone starts the workflow |

No start events means anyone may start the workflow. Otherwise a start is
admitted when *any* event admits it (403 when none does, 409 when every
event is of the last two kinds); the admitting event's id is recorded on
the instance. A start event's `id` shares the task id namespace, so that a
task's `dependsOn` can later name it.

A `TRIGGERING_EVENT` start fires on its own. Its `eventType` names an
entry of the organization's event catalog (base-event's `EventDefinition`,
e.g. `OrderCreatedEvent` = an `order` was created), checked when the
workflow is saved. When base-entity or base-state publishes a matching
platform fact, base-event republishes it as `DefinedEventOccurred` and
base-workflow starts every workflow whose start event names it: the
event's subject becomes the instance's `entityId`, and `payloadMapping`
copies values into the initial context — `$.subjectId`,
`$.payload.<attribute>`, evaluated against the whole event. A subject that
already has a running instance of the workflow is not started twice. The
events travel through the Spring Modulith publication registry, so a start
is retried until it completes.

Still not implemented: an `INPUT_ARTIFACT` reaching its state on its own
(it only guards explicit starts), `TIME_BASED_PRECONDITION`, and MESSAGE /
SIGNAL events thrown or caught inside a workflow.

Along the flow, `TaskUse.artifactStates` record the state a task expects
each input in and leaves each output in (SPEM's work product state). The
modeler draws one object node per artifact *and state* — `new_order :
Order [CONFIRMED]` — so the same artifact appears once per state it passes
through, and each object sits between the task producing it and the task
consuming it. Recorded and drawn, not yet enforced at run time.

There is no stored end event: an instance completes when every task is
terminal, and the modeler draws that as one End node fed by every task
nothing depends on.

---

## Summary Table

| Concept | Definition (Method Content) | Use (Process, workflow-scoped) | Runtime |
|---|---|---|---|
| Task | TaskDefinition | TaskUse | TaskInstance |
| Artifact | ArtifactDefinition | ArtifactUse | ArtifactInstance |
| Role | RoleDefinition | RoleUse | Assignment |
| Tool | ToolDefinition | ToolUse | ToolInvocation |

Plus one orthogonal, organisation-scoped concept: **ToolConfiguration**
(credentials/connection details per tenant).

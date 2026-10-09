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
| `TIME_BASED_PRECONDITION` | Time-based Precondition / Milestone Guard | never — its timer starts the workflow |

No start events means anyone may start the workflow. Otherwise a start is
admitted when *any* event admits it (403 when none does, 409 when every
event is of the last two kinds); the admitting event's id is recorded on
the instance. A start event's `id` shares one namespace with the task and
intermediate-event ids, which `dependsOn` names.

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

Two more start types fire on their own:

- **`INPUT_ARTIFACT`** — when the subject reaches the required state. A
  platform fact matches a required ENTITY artifact when its entity type is
  the artifact's and either the artifact names no state and the object was
  created, or the object entered exactly the state named. The remaining
  required artifacts are checked as an explicit start checks them; then
  the workflow starts for that subject, `payloadMapping` reading the fact
  (`$.subjectId`, `$.state`, `$.payload.<attribute>`). base-state reports
  an object's initial state too, so a start waiting for `DRAFT` fires on
  creation. Deduplicated per subject like a triggered start.
- **`TIME_BASED_PRECONDITION`** — when its `timer` comes due: a DATE fires
  once, a CYCLE repeatedly (see *Timers* below). A DURATION is refused, as
  it would have nothing to be relative to. A scheduled instance has no
  subject. The milestone and the precondition are recorded but not yet
  evaluated.

## Intermediate events

While it runs, a workflow can raise catalogued events and wait for them —
BPMN's intermediate throw and catch events. `Workflow.events` holds
**EventUses**, each naming an `EventDefinition` of base-event's catalog and
a `direction`. An event takes part in the flow like a task: its
`dependsOn` decides when it is reached, a task's `dependsOn` may name it,
and the whole flow must be acyclic.

- **THROW** — reached means done. The engine publishes `EventThrown`;
  base-event checks the definition (MESSAGE or SIGNAL — SYSTEM events are
  the platform's) and republishes it as `DefinedEventOccurred`, so it is
  the only publisher of occurrences. `payloadMapping` builds the payload
  out of the instance context (`$.<variable>`); the occurrence's subject is
  the throwing instance's subject.
- **CATCH** — reached means WAITING; the event occurring makes it
  OCCURRED, and `payloadMapping` copies values out of the event
  (`$.payload.<attribute>`, `$.correlationValue`, …) into the context.

How an occurrence finds its catches depends on the definition's kind:

| Kind | Delivered to | Matched on |
|---|---|---|
| SYSTEM | every waiting catch | its subject — the instance's `entityId` |
| MESSAGE | exactly one waiting catch, the longest waiting, never in the thrower | `correlationKey`: both sides read the named context variable |
| SIGNAL | every waiting catch | — |

A MESSAGE nobody waits for is dropped — there is no buffer — and a message
that starts a workflow through a TRIGGERING_EVENT start event needs no
catch at all: that is how one workflow asks another for work. Delivery runs
through the publication registry like a start, so it is retried until it
completes, and a redelivered occurrence is recognised by its id. A catch
that every dependent has moved past — an ANY-join that went ahead on
another branch — is withdrawn, and cancelling an instance withdraws its
catches.

### Timers

A CATCH may wait for time instead of a catalogued event: a `timer` with a
`type` and an `expression` takes the place of `eventDefinitionId` — an
event has exactly one of the two.

| `type` | Fires | Literal |
|---|---|---|
| `DURATION` | once, that long after it is reached | `PT2H`, `P3D`, `P1M` |
| `DATE` | once, at that moment | `2026-10-10T08:00:00Z`; a date is read as UTC midnight |
| `CYCLE` | repeatedly, at that interval | `R3/PT1H`, `R/PT1H` (unbounded), `R3/2026-10-10T08:00:00Z/P1D` (anchored) |

Years and months are calendar amounts, applied in UTC. Instead of a
literal, the expression may be a `$.variable` path into the instance
context, read when the timer is reached — `$.deadline` waits for whatever
date an earlier task recorded. A path that resolves to nothing leaves the
timer waiting without a due time. Firings of an anchored cycle already in
the past are skipped, not caught up.

A reached timer waits with a due time; a sweep that runs every 30 seconds
fires what is due — polling rather than a scheduler or a broker, so the
granularity is the sweep interval. Every firing publishes
`TimerFiredEvent`, the hook for a reminder. A CYCLE is allowed only on a
non-interrupting boundary event: anywhere else it would have nothing to
stop it.

### Boundary events

With `attachedTo` naming a task, a catch is a **boundary event** of that
task — the escalation of BPMN. It has no `dependsOn` of its own: it starts
waiting when its task becomes ACTIVE, and is cancelled when the task ends
first. Its dependents name it in their `dependsOn` like any other event.
When it fires:

- an **interrupting** one (the default) cancels the task, which becomes
  CANCELLED with the reason `interrupted by <event id>`;
- a **non-interrupting** one leaves the task running — a CYCLE fires again
  until its repetitions run out or the task ends.

Either way the boundary's dependents go ahead. A timer or any catalogued
event — MESSAGE, SIGNAL, SYSTEM — may be a boundary.

### Dead-path elimination

A CANCELLED task does not satisfy its dependents the way a COMPLETED or
SKIPPED one does, so whatever can no longer be reached is cancelled too,
rather than left waiting forever. A pending task or event is dead when its
join is ALL and any dependency was cancelled, or its join is ANY and every
dependency was; dead tasks are cancelled as `unreachable`. That is what
makes the two paths of a boundary exclusive. In the testbed's invoicing
workflow `issue-invoice` carries an interrupting `PT1H` timer leading to
`escalate-invoice`, and the `invoice-issued` throw depends on either with
`joinType: ANY`: issued in time, the timer is cancelled with its task and
the escalation becomes unreachable; overdue, `issue-invoice` is
interrupted, its successors become unreachable, and the escalation answers
instead.

Along the flow, `TaskUse.artifactStates` record the state a task expects
each input in and leaves each output in (SPEM's work product state). The
modeler draws one object node per artifact *and state* — `new_order :
Order [CONFIRMED]` — so the same artifact appears once per state it passes
through, and each object sits between the task producing it and the task
consuming it. Recorded and drawn, not yet enforced at run time.

There is no stored end event: an instance completes when every task and
every intermediate event is terminal — a CANCELLED task counts — and the modeler draws that as one End
node fed by every task or event nothing depends on.

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

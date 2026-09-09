# Open questions

Decisions surfaced while designing and then building the task dashboard. Three of the five original
questions are answered — one of them differently from how the design assumed — and one new one was
found while verifying the screens against a running backend.

Statuses below are as of the dashboard being implemented (`src/lib/feature/dashboard`,
`src/lib/domain/dashboard`), not as of the original brainstorm.

## 1. Generic completion forms — **still open, and now the biggest limit**

`TaskDefinition` declares nothing about what a task's completion is expected to contribute to
`CompleteTaskRequest.context`. Two paths:

- **(a)** Keep it implicit — each `USER_STEP.description` is read by a human, and the frontend
  hand-builds one completion form per task type.
- **(b)** Add a small declarative field (to `TaskStepDefinition` or `TaskDefinition`) naming the
  expected context keys and their input type, so the dashboard can render a generic form.

**What was built:** a generic **key/value editor**, not the free-text note the first sketch had.
`context` is merged into the workflow context before the postcondition rule runs, and a rule reads
*named* variables (`reviewScore`, `approved`) — a single `note` field would produce one key no rule ever
references, which looks like a working form and contributes nothing. Named pairs are the smallest thing
that can actually satisfy a postcondition.

**What it still cannot do:** label the fields, validate them, or offer the right ones. The user has to
know the key names. That is what (b) fixes, and it is worth doing before the number of distinct task
types grows — the alternative is one bespoke component per task.

## 2. Claim-race protection — **still open, backend decision**

`assignTask` returns `409` only when the task is not `ACTIVE`. It does **not** refuse a task already
assigned to somebody else, so two teammates claiming the same row in the same moment silently resolve
last-write-wins. See [screens/04-assign-claim-flow.md](screens/04-assign-claim-flow.md).

The frontend cannot close this. `WorkflowDashboardStore.claim` re-reads the runs afterwards, so the loser
sees whose task it now is instead of a stale screen — that is mitigation, not a fix. Either:

- **accept it** — rare at normal task volumes, or
- **close it** — have `assignTask` answer `409` when `assignedTo` is already set and does not match the
  caller.

## 3. Cross-instance task queries — **answered: no endpoint needed**

The original ask was for `GET /organizations/{orgKey}/tasks?assignedTo=…&status=…`, on the assumption
that an inbox spanning many runs would otherwise have to fan out per instance.

It does not. `listWorkflowInstances` answers with **full** `WorkflowInstance` objects, tasks and artifacts
nested — deliberately, and the contract says why on `listWorkflows`: the list endpoint returns the same
shape the single GET returns, so there is no lighter projection. One collection read therefore yields
every task of every run *together with the run it belongs to*, which is what
`WorkflowDashboardStore.allTasks` flattens.

This also answers the design package's second "backend change needed" note — that `TaskInstance` lacks a
`workflowInstanceId`. It does lack one, and it does not need one: the run is known at the moment the task
is read. `DashboardTask` keeps it beside the task rather than asking the server to repeat it.

**The limit that replaces it:** the inbox is as wide as the instance list it derives from, and that list
is paged like every other base-entity list. At a volume where one page no longer holds a user's open work,
the convenience endpoint becomes the right fix — a fan-out never is.

## 4. Semantic artifact state — **still open, deliberately unresolved**

`ArtifactInstance.currentState` is an arbitrary string from whichever base-state machine is attached, and
nothing in the contract says whether a given state name is good, bad or neutral. The chips are therefore
neutral; see [screens/03-artifact-state-chips.md](screens/03-artifact-state-chips.md).

Note the contrast the implementation makes explicit: **task status is coloured, artifact state is not**,
and that asymmetry is the contract's rather than a matter of taste. `TaskInstanceStatus` is a closed enum
base-workflow assigns itself, so colouring `BLOCKED` red states a fact. If semantic colouring of artifact
state is wanted, the fix is an outcome/category field on base-state's state definition surfaced through
`ArtifactInstance` — not string-matching in a component.

## 5. Per-step user acknowledgment — **still open; client-side for now**

`USER_STEP` checkboxes are local to the open task and reset when a different one is selected
(`linkedSignal` in `StepChecklistComponent`). Nothing persists them, because no endpoint exists to mark
one step done. Decide whether steps are guidance (sufficient as-is) or tracked sub-state (needs an
endpoint and a `StepResult` for user steps).

## 6. `taskId` in the contract means `taskDefinitionId` — **new, and a trap**

Found while driving the finished screens against a running backend. Every task-scoped endpoint —
`getTaskInstance`, `assignTask`, `completeTask`, `skipTask` — resolves the row through
`TaskInstanceRepository.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId`, and the backend's own
parameter is named `taskDefinitionId` (see `AssignTaskUseCase.assign`). Passing `TaskInstance.id` — the
UUID every read endpoint returns — answers `404 workflow.notFound`.

The addressing itself is defensible: a task appears at most once per workflow, so its definition id is
unique within a run. What makes it a trap is that `base-workflow-api.yaml` declares the parameter as a
bare `taskId: string` with **no description**, while `TaskInstance.id` is present, is a UUID, and is the
obvious candidate. The original design package assumed the UUID throughout.

Two things worth deciding:

- **Say so in the contract.** The parameter now carries a description
  (`components.parameters.TaskIdParam`) stating that it is the `taskDefinitionId`. That is the minimum.
- **Or make `TaskInstance.id` work too.** If the UUID is meant to be an addressable identity, the
  endpoints should accept it — otherwise consider why it is exposed at all, since no client can act on it.

Until then, `TaskActionService` and `ActionTarget` exist partly to make the right id the only one a call
site can pass.

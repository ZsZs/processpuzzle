# ProcessPuzzle — workflow task dashboard (design)

The end-user "My Tasks" dashboard: the screen that lets a user see their tasks, claim, complete or skip
them, navigate to the artifacts a task reads and writes, and follow its steps. Designed against
`base-workflow-api.yaml` and **implemented** — see the status section below.

It complements, and does not replace, the modeler's activity-diagram presentation of task sequencing
(`dependsOn` / `joinType` / `parallel`), which is the right place to visualize workflow *structure*. This
dashboard is about *doing the work*.

## Status

Implemented and mounted. `WORKFLOW_DASHBOARD_ROUTES` puts it at `workflow-dashboard`; the testbed mounts
it under `/base-workflow/samples` and redirects that path to it, so the dashboard is what
`http://localhost:4200/base-workflow/samples` opens.

| Screen | Component | State |
| --- | --- | --- |
| 1 — [My tasks inbox](screens/01-my-tasks-inbox.md) | `TaskListComponent` + `WorkflowDashboardComponent` | done |
| 2 — [Task detail](screens/02-task-detail.md) | `TaskDetailComponent`, `StepChecklistComponent`, `ArtifactPanelComponent`, `CompletionFormComponent` | done; the completion form is generic — open-questions #1 |
| 3 — [Artifact state chips](screens/03-artifact-state-chips.md) | `ArtifactChipComponent` | done, all three treatments |
| 4 — [Assign / claim flow](screens/04-assign-claim-flow.md) | claim button on the row + locked detail pane | done; the claim race is open — open-questions #2 |
| 5 — [Process-owner Kanban](screens/05-process-owner-kanban.md) | `ProcessBoardComponent` | done |

This is where the four runtime verbs the library README listed as having "no front-end surface yet" get
one — `/assign`, `/complete` and `/skip`. `POST /instances` still has none, deliberately: starting a run is
not a task somebody was assigned.

## How it differs from the original brainstorm

The design survived contact with the codebase; four assumptions did not.

**No new endpoints, and no `resource()` data layer of its own.** The proposal assumed a cross-instance
task query had to be added and a `WorkflowApiService` written to call it. Neither was needed:
`listWorkflowInstances` already answers with full instances, tasks nested, and `WorkflowInstanceStore`
already reads it. The dashboard derives every row from that store plus the four catalogs, so it shares one
cache with the generated Workflow Instance screens instead of keeping a second. See open-questions #3.

**A task is addressed by its `taskDefinitionId`, not `TaskInstance.id`.** Found by driving the finished
screens against a running backend. This one is a genuine trap and is written up as open-questions #6.

**No `orgKey` anywhere.** In this workspace the tenant is part of the configured service root
(`WORKFLOW_SERVICE_ROOT`, falling back to `APP_SERVICE_ROOT`), so no screen threads an organization through
its calls and `CurrentUserContext` does not carry one.

**The completion form takes named key/value pairs, not a free-text note.** A rule reads named variables, so
a `note` key nothing references would be a form that appears to work and contributes nothing.

## Cross-cutting decisions

- `WorkflowInstance.context` — the accumulated key/value context — is **not** shown. It stays internal to
  rule evaluation.
- Artifact state chips are **neutral**; task status badges are **coloured**. The asymmetry is the
  contract's: `TaskInstanceStatus` is a closed enum base-workflow assigns, `ArtifactInstance.currentState`
  is an arbitrary string from whichever base-state machine is attached. See open-questions #4.
- Enum values are shown **untranslated** — `ACTIVE` reads as `ACTIVE` — because nothing else in this
  workspace translates one, and a dashboard that did would disagree with the Task Instance list one route
  away.
- "Claim" and "assign" are one call: `POST …/assign` with the acting user's own id, or a chosen user's.
- Skip is gated to a stated `process-owner` role rather than shown beside Complete: the contract calls it a
  manager override. It is a UI gate, not a security boundary.
- The process view is a plain Kanban grouped by `TaskInstanceStatus` — one endpoint, no cross-referencing.
  Sequence visualization stays with the modeler rather than being duplicated here.
- Plain HTML controls throughout, no `@angular/material`: this library declares no Material peer
  dependency, which is why the modeler's toolbar is bare checkboxes and its Save is a bare `<button>`.

## What the host application still has to provide

A `CurrentUserContext` implementation. The dashboard needs to know which user and which roles, and
`@processpuzzle/auth` is not a dependency of this library — a task list is not a reason for a feature
library to depend on the authentication one. The testbed's `SessionUserContext` reads
`AuthService.currentUser`.

**Roles are the gap.** `User` carries none, in either the Firebase or the Keycloak implementation, so no
host can forward them yet. The consequences are deliberate and documented on `CurrentUserContext`: an
unstated role list means *unknown*, so the Team queue offers every claimable task (the backend still
refuses a claim by a user without the role) while the Skip override stays hidden (an unstated role is not
a granted one). Wiring real roles starts by surfacing them on `User`.

See [open-questions.md](open-questions.md) for what is still undecided.

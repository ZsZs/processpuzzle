# Screen 1 — My tasks inbox

Two-pane layout: a filterable task list on the left, the selected task's workspace on
the right. This is the screen a normal user lands on and works from.

## Left pane — the list

Three tabs, all reading `TaskInstance` rows but scoped differently:

| Tab | Scope | Notes |
|---|---|---|
| My tasks | `assignedTo == currentUser` | Primary working queue |
| Team | `assignedTo == null` and `status == ACTIVE`, filtered to roles the user holds | Claimable queue — see screen 4 |
| Process | all tasks for one `WorkflowInstance` | Supervisor / debugging view |

Each row shows: task name, status badge (`TaskInstanceStatus`: `PENDING` / `ACTIVE` /
`COMPLETED` / `SKIPPED` / `BLOCKED`), the workflow instance / entity it belongs to, and
— when `BLOCKED` — a one-line `blockedReason` shown inline or on hover, since that field
is already on the object and costs nothing extra to display.

**Data need not yet in the contract:** a cross-instance "my tasks" query. The current
API lists tasks per instance (`GET /instances/{instanceId}/tasks`); a real inbox spans
many instances at once. Either add a convenience endpoint
(`GET /organizations/{orgKey}/tasks?assignedTo=me`) or have the frontend fan out across
`listWorkflowInstances` + per-instance task lists — the former is strongly preferred at
any real task volume.

## Right pane — the task workspace

Selecting a row loads the task's detail view — see
[screen 2](02-task-detail.md) for the steps checklist, artifact panel, and completion
form.

## API calls

- `GET /organizations/{orgKey}/instances/{instanceId}/tasks` (or the proposed
  cross-instance equivalent) — populate the list
- `GET /organizations/{orgKey}/instances/{instanceId}/tasks/{taskId}` — load detail on
  selection

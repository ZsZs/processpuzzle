# Screen 5 — Process-owner Kanban

A status-grouped board for one `WorkflowInstance`, for whoever owns the process rather
than a single task.

## Layout

Columns are exactly `TaskInstanceStatus`: `PENDING`, `ACTIVE`, `BLOCKED`, `COMPLETED`
(`SKIPPED` folds into `COMPLETED`'s column, or gets its own thin column if it's frequent
enough to matter). One header strip above the board shows the instance itself:
workflow name, linked entity, `WorkflowInstanceStatus`, `startedAt`.

Each card shows: task name, and — depending on column — assignee (`ACTIVE` /
`PENDING`), `blockedReason` (`BLOCKED`, already on the object, no extra call needed), or
`completedAt` (`COMPLETED`).

## Deliberately out of scope here

This board shows *what's* stuck, not *where* in the sequence it sits relative to
siblings, or whether a `parallel` group is half-done. That's sequencing information,
and it's already covered by the existing activity-diagram presentation
(`TaskUse.dependsOn` / `joinType` / `parallel`, or the persisted `WorkflowDiagram`
nodes/edges). Duplicating that here would mean maintaining two layouts of the same
structure — not worth it unless the Kanban view turns out to be insufficient in
practice. If it does, the better move is layering live per-task status onto the
existing diagram's nodes rather than building a second graph view from scratch.

## API calls

- `GET /organizations/{orgKey}/instances/{instanceId}` — instance header info
- `GET /organizations/{orgKey}/instances/{instanceId}/tasks` — populate all columns in
  one call

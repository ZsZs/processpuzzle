# Screen 4 — Assign / claim flow

Covers the "Team" tab from screen 1: `ACTIVE` tasks with `assignedTo == null`, visible
to any user holding one of the task's `performedByRoles`.

## Behavior

- Each unassigned row shows a **Claim task** button. Clicking it calls
  `POST /tasks/{taskId}/assign` with `{ userId: currentUser.id }`.
- A manager assigning someone else uses the same endpoint with a chosen `userId` — same
  call, different source for the value, so no separate "assign" vs "claim" backend
  concept is needed.
- The right pane stays locked (steps, artifacts, and the completion form hidden) until
  the task is claimed — showing a form nobody's allowed to submit yet is worse than
  showing nothing.

## Known gap: no claim-race protection

`assignTask` only returns `409` when the task isn't `ACTIVE` — the contract does not
reject assigning a task that's already `assignedTo` someone else. Two teammates
claiming the same row within the same moment would silently reassign it rather than
fail with "already taken" (last write wins). This is a backend decision, not a frontend
one:

- **Accept it** — rare in practice at normal task volumes, or
- **Close it** — have `assignTask` reject with `409` when `assignedTo` is already set
  and doesn't match the caller.

Flagging for a decision before this ships; the frontend can't fully paper over a
backend race.

## API calls

- `GET /organizations/{orgKey}/instances/{instanceId}/tasks?status=ACTIVE` — filter to
  candidates, then filter client-side to unassigned + matching role
- `POST /organizations/{orgKey}/instances/{instanceId}/tasks/{taskId}/assign`

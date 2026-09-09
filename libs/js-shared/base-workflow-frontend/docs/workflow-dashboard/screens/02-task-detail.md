# Screen 2 — Task detail: steps and completion

The task workspace has three sections stacked in the right pane: a step checklist, an
artifact panel, and a completion form. These are three different concerns and shouldn't
collapse into one — the checklist is "how do I do this task," the artifact panel is
"what do I need / what will this produce," and the form is "how do I finish it."

## Step checklist

Reads `TaskDefinition.steps` (ordered `TaskStepDefinition[]`) joined with
`TaskInstance.stepResults` for what already happened.

- **`USER_STEP`** — informal guidance, not enforced by the engine. Rendered with a
  checkbox and its `description` text underneath. The checkbox is **client-side only**:
  the API has no endpoint to mark an individual user step done, so checking it off is a
  local progress aid, not something that round-trips until the whole task is completed.
- **`SERVICE_STEP`** — the engine already called (or will call) a tool operation via
  `inputMapping` / `outputMapping`. Rendered as a passive status row, not a checkbox —
  the user doesn't "do" this step. Shows the matching `StepResult.toolResponse` summary
  when present, and surfaces `StepResult.error` prominently if the tool call failed.

## Artifact panel

Lists the task's declared `inputs` / `outputs` (`TaskDefinitionInput.inputs` /
`.outputs`, both arrays of `ArtifactDefinition` ids), each resolved against the
instance's `ArtifactInstance[]` by `artifactDefinitionId`. See
[screen 3](03-artifact-state-chips.md) for how each artifact's state is represented.
An output with no matching `ArtifactInstance` yet is shown as "not created yet" rather
than omitted — it tells the user what the task is expected to produce.

## Completion form

`POST /tasks/{taskId}/complete` takes `CompleteTaskRequest.context` — arbitrary
key/value pairs merged into the workflow's context before the postcondition rule runs.
The form's fields are therefore **task-specific** and not currently derivable from the
contract; see open-questions.md for the proposal to make this generic. Submitting calls
`complete`; the response is `CompleteTaskResponse { accepted, task, postconditionDetail }`
— on `accepted: false`, render `postconditionDetail` as inline validation text next to
the form, not a toast, since the task stays `ACTIVE` and the user needs to fix and
resubmit.

`Skip` posts to `/tasks/{taskId}/skip` with a `reason` — a manager override, so this
action should be gated separately from `complete` (e.g. only shown to users with an
elevated role) rather than presented as an equal peer button.

## API calls

- `GET /organizations/{orgKey}/tasks/{taskId}` (definition) — steps, inputs/outputs
- `GET /organizations/{orgKey}/instances/{instanceId}/tasks/{taskId}` — instance state,
  `stepResults`
- `GET /organizations/{orgKey}/instances/{instanceId}/artifacts` — resolve input/output
  artifact instances
- `POST /organizations/{orgKey}/instances/{instanceId}/tasks/{taskId}/complete`
- `POST /organizations/{orgKey}/instances/{instanceId}/tasks/{taskId}/skip`

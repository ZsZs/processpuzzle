# Screen 3 — Artifact state chips

`ArtifactInstance.currentState` is a bare string cached from base-state — whatever the
attached state machine's author named it ("draft", "pending review",
"under_investigation"...). Nothing in `base-workflow-api.yaml` or the referenced
base-state model tells the dashboard whether a given state name is good, bad, or
neutral. Three deliberately distinct treatments, all neutral by design:

1. **Instance exists, has a `currentState`** — neutral gray pill:
   `{type} · {currentState}`, the literal string from the API, type icon
   (`DOCUMENT` / `ENTITY` / `WIDGET`) for quick scanning.
2. **Instance exists, `currentState` is null** — no state machine attached to this
   artifact. Show only the type label, no state segment.
3. **No `ArtifactInstance` yet** — a declared task output (`TaskDefinition.outputs`)
   that hasn't been produced. Dashed border, muted "not created yet."

## Why not color-code by meaning

Pattern-matching on state name strings ("approved" = green, "rejected" = red) is
fragile — it breaks silently the moment a new state machine uses different vocabulary,
and it embeds business meaning in the frontend that belongs in the domain model.

**If semantic coloring is wanted later**, the correct fix is a contract change: add an
outcome/category field (e.g. `NEUTRAL | POSITIVE | NEGATIVE`, or a terminal flag) to
base-state's state definition, and have `ArtifactInstance` surface it alongside
`currentState`. That's a base-state + base-workflow change, not a frontend-only one —
call this out explicitly if/when it's prioritized, rather than working around it with
client-side heuristics.

## API calls

- `GET /organizations/{orgKey}/instances/{instanceId}/artifacts` — the source of
  `ArtifactInstance[]` these chips render from

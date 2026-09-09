# ProcessPuzzle E2E Living-Docs Sketch

A working sketch of a single, deep E2E scenario — Order entity → OrderDocument
→ PPCL rules → state machine → SPEM workflow — that doubles as generated
documentation, without a separate Gherkin/prose layer to keep in sync by hand.

## Why this shape

- **Fixtures are the spec, not a description of it.** `fixtures/order-scenario/*.yaml`
  are real ProcessPuzzle metadata (entity definitions, PPCL rules, state
  machine, SPEM workflow). The doc generator embeds this YAML verbatim —
  there's nothing to keep in sync because there's only one copy.
- **The manifest is the single source of truth for narrative + wiring.**
  `scenarios/order-lifecycle.manifest.ts` lists each step once, with its
  title (used as both the `test.step()` label and the doc heading), which
  fixtures it touches, and what it actually does. The spec file
  (`tests/order-lifecycle.e2e.spec.ts`) just iterates the manifest — it has
  almost no logic of its own, so there's no second narrative to drift from
  the first.
- **Execution evidence, not hand-written pass/fail claims.** The doc
  generator reads Playwright's JSON reporter output to show real status and
  duration per step, plus (via `trace: 'on'`) a full trace per run.

## Layout

```
fixtures/order-scenario/   the metadata itself (entity, document, rules, state machine, workflow)
support/
  metadata-fixture-loader.ts   loads + caches YAML fixtures, exposes raw text for doc embedding
  platform-fixture.ts          STUB — swap for the real @processpuzzle/e2e-testing platform fixture
scenarios/
  order-lifecycle.manifest.ts  single source of truth: step titles, fixture refs, run() behavior
tests/
  order-lifecycle.e2e.spec.ts  thin — iterates the manifest inside test.step()
scripts/
  generate-living-docs.ts      manifest + fixtures + results.json -> docs/order-lifecycle.living-doc.md
```

## Running it

```bash
npm install
npx playwright install   # if browsers aren't already present
npm run docs:build       # runs the E2E test, then generates the living doc
```

Output: `docs/order-lifecycle.living-doc.md`, plus the standard Playwright
HTML report (`playwright-report/`) and trace files for deeper debugging.

## What's a stub vs. what's real design

- `support/platform-fixture.ts` is an **in-memory fake** so this package runs
  standalone without a live backend. Replace its `platform` fixture with the
  real one from `@processpuzzle/e2e-testing` (talking to base-entity,
  base-rule, base-state, base-workflow over their real `base-*-api.yaml`
  contracts) — nothing else in the package needs to change, since the
  manifest and doc generator only depend on the `PlatformContext` interface.
- `scripts/generate-living-docs.ts`'s JSON-report flattening matches
  `@playwright/test` ^1.47's reporter shape. Check `test-results/results.json`
  against `flattenStepResults()` if you're on a different version — the
  reporter schema has shifted across major versions before.
- Everything else (fixtures, manifest, spec, loader) is meant as a real
  starting point, not throwaway scaffolding.

## Extending to a second scenario

Add a new `fixtures/<scenario>/*.yaml` set and a new
`scenarios/<scenario>.manifest.ts`, point a new spec file at it, and add a
second doc-generation pass in `generate-living-docs.ts` (or generalize it to
take a manifest path as an argument — the current version is scoped to one
scenario for clarity).

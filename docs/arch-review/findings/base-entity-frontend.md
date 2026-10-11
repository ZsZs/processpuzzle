# Architecture review: base-entity-frontend

- **Slice code:** FEN
- **Paths in scope:** `libs\js-shared\base-entity-frontend`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** Static IDE file discovery/read, scoped glob/ripgrep, file and numbered PowerShell reads, and Git revision verification. Applied the existing rubric and architectural intent from `AGENTS.md`/`README.md`; inspected the slice's Angular 22 peer manifest rather than using README's older version labels. Traced form construction/submission, stores and REST responses, descriptors and dynamic registry reset, grid/query/export, rule integration and relevant tests. Tests inspected, not executed; no builds, tests, Nx tasks or lint were run.
- **Estimation note:** Effort is provisional because rubric calibration has not been completed. Rubric scales and effort points are unchanged.

## Summary

The library provides substantial reusable metadata interpretation, including generated controls, embedded aggregates, descriptors and persistence adapters. The highest-priority defects are a command-completion contract that discards failed-save drafts (FEN-API-01) and rule-loading state that permits unchecked submission (FEN-MET-01). Runtime descriptor replacement also leaves already-resolved DI tokens pointing at obsolete facades (FEN-STA-01). Additional debt concerns collisions between authored attributes and persistence identity, server-page versus client-list semantics, and advisory rule feedback becoming blocking form errors. These findings concern concrete execution paths and state ownership, not RxJS usage, styling or planned scaffolding.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FEN-API-01 | Failed persistence still completes the form's success navigation | API | 4 | 3 platform | M | High | 6.0 |
| FEN-MET-01 | Unloaded or failed rule metadata is treated as no rules | MET | 4 | 3 platform | M | High | 6.0 |
| FEN-STA-01 | Descriptor reset does not invalidate injected facade instances | STA | 4 | 2 feature | M | High | 4.0 |
| FEN-PER-01 | Flattened payload attributes collide with row identity and version | PER | 5 | 3 platform | L | High | 3.8 |
| FEN-API-02 | The grid treats one server page as the complete collection | API | 4 | 3 platform | L | High | 3.0 |
| FEN-MET-02 | Field-level advisory violations become blocking Angular errors | MET | 3 | 2 feature | M | High | 3.0 |

## Findings

### FEN-API-01 — Failed persistence still completes the form's success navigation

- **Category:** API
- **Criticality:** 4
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 6.0
- **Evidence:**
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-store\addEntity.ts:9` — a failed request sets store error state and resolves with `undefined`.
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-store\updateEntity.ts:20` — update failures are also caught and returned as `undefined`, not rejected promises.
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.ts:104` — submission clears the current entity, awaits add/update without examining its result, and always navigates back.
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.ts:95` — deletion likewise navigates back after awaiting a command whose failure is swallowed at `libs\js-shared\base-entity-frontend\src\lib\base-entity-store\deleteEntity.ts:22`.
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-store\base-entity.store.spec.ts:134` — the inspected failure test verifies store error state, while form submission coverage at `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.spec.ts:65` exercises the success path.
- **Why it is debt:** The store and form disagree about whether a resolved command means successful persistence. A validation, conflict or transport failure leaves the stored row unchanged but sends the user away from the draft just as a successful save would, making correction or retry lose the active editing surface. This has platform blast radius because the same generated form and command contract serve unrelated entity types and feature authoring screens.
- **Suggested refactoring:** Give mutation commands a consistent discriminated success/failure result, or consistently reject failures, and make navigation explicitly conditional on success. Preserve the draft, selection and actionable error on failure; put pending state on the command so repeat submission is prevented while it runs. Add form-plus-store tests for rejected creation, update conflict and failed deletion, checking both retained form values and absence of navigation.
- **Depends on / blocks:** none

### FEN-MET-01 — Unloaded or failed rule metadata is treated as no rules

- **Category:** MET
- **Criticality:** 4
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 6.0
- **Evidence:**
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.ts:58` — the initial rule set is an empty array, without a separate loading/failed/ready state.
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.ts:66` — Save eligibility checks form validity and existing violations but not rule-load completion.
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.ts:128` — evaluation returns immediately for an empty rule set, after which submission at lines 104–114 can persist.
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.ts:172` — the asynchronous load only assigns rules on success and logs on error; arrival does not evaluate the already-edited candidate.
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.spec.ts:151` — the main rule fixture emits synchronously with `of(rules)`; the load-failure test at line 166 checks logging rather than save eligibility.
- **Why it is debt:** “No configured rules” and “rules not yet known” have the same representation at the form's enforcement boundary. A user can submit while metadata is in flight, or after its request fails, and the frontend will run no configured constraints; a later response cannot validate a submission that already left the form. The generic validation seam is shared across features, so this is platform-level frontend consistency debt, without asserting anything about server-side enforcement.
- **Suggested refactoring:** Model rule readiness explicitly and require a successful context-specific load before a configured rule engine can authorize submission. Keep the deliberately absent-engine case distinct, expose failed-load recovery, and evaluate the current draft when metadata arrives. Test delayed success, delayed failure, retry and context changes with controlled asynchronous responses.
- **Depends on / blocks:** FRU-API-01

### FEN-STA-01 — Descriptor reset does not invalidate injected facade instances

- **Category:** STA
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-definition\dynamic-entity.registry.ts:96` — reset clears resolved entities, descriptor cache and definition cache, but retains the facade tokens.
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-definition\dynamic-entity.registry.ts:112` — each entity reuses its root-provided token; its factory returns the current facade object, not a delegating handle.
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-definition\dynamic-entity.registry.ts:140` — rebuilding creates a new facade and publishes its descriptor/store in the registry.
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity.routes.ts:86` — an embedded route aliases `ACTIVE_ENTITY_FACADE` to the supplied token through `useExisting`.
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-embedded\embedded-entity-host.component.ts:27` — the host injects that facade and obtains the descriptor from the injected object.
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-definition\dynamic-entity.registry.spec.ts:132` — the reset test injects the token only after rebuilding, so it does not exercise a token already resolved by the injector before reset.
- **Why it is debt:** The registry invalidates its own map but not Angular's cached root-provider result. Once a route has injected a token, reset followed by resolve publishes a new descriptor/store through registry lookup while injection of that same token still returns the old facade. The metadata-defined screen can consequently mix generations of descriptors and stores instead of reflecting the edited definition consistently.
- **Suggested refactoring:** Choose one explicit generation/lifetime model: a stable facade handle that delegates to the current generation, or generation-scoped tokens/injectors with routes rebuilt and obsolete scopes destroyed together. Ensure descriptor and store replacement are atomic from every consumer's perspective. Extend the reset test to inject before reset, modify the definition, resolve again, and compare registry and routed-host behavior.
- **Depends on / blocks:** none

### FEN-PER-01 — Flattened payload attributes collide with row identity and version

- **Category:** PER
- **Criticality:** 5
- **Blast radius:** 3 platform
- **Effort:** L
- **Confidence:** High
- **Priority score:** 3.8
- **Evidence:**
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-authoring\entity-attribute.descriptors.ts:22` — authored attribute codes are editable required text, with no reserved-name restriction for `id` or `version`.
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-definition\dynamic-entity.mapper.ts:41` — payload fields are flattened, then envelope identity/version overwrite same-named authored values.
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-definition\dynamic-entity.mapper.ts:64` — serialization destructures `id` and `version` out of the entity and excludes them from the outgoing payload.
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.ts:105` — edited form values overwrite fields on the flat entity before it is passed to persistence.
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-service\base-entity-rest.service.ts:117` — update derives the URL identity from that entity's `id`.
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-definition\dynamic-entity.mapper.spec.ts:26` — the inspected collision test requires envelope values to win, but does not verify preservation of authored collision values through a read/edit/write round trip.
- **Why it is debt:** Persistence metadata and user-defined business fields occupy the same namespace even though the authoring surface permits both names. A payload containing business `id` or `version` loses that value on read and omits it on write; editing an `id` control can also change the identity used to construct the update request instead of editing a business attribute. The foundation's shared flat model, form binding and persistence contract make this platform representation debt, with a concrete data-loss/request-target risk for affected definitions.
- **Suggested refactoring:** Separate envelope identity/version from authored values and make store keys, form accessors and mapper round trips use the appropriate namespace explicitly. If reserved names are an intentional interim restriction, enforce it consistently in metadata ingestion and authoring and surface existing incompatible definitions rather than silently discarding fields. Include compatibility handling and tests for collision values, correct update targets and preservation of unaffected fields.
- **Depends on / blocks:** none

### FEN-API-02 — The grid treats one server page as the complete collection

- **Category:** API
- **Criticality:** 4
- **Blast radius:** 3 platform
- **Effort:** L
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-store\findByQuery.ts:21` — paged responses retain `content` as entities and separately store server page/count metadata.
  - `libs\js-shared\base-entity-frontend\src\lib\base-list\base-entity-list.component.ts:85` — the grid attaches a client-side `MatTableDataSource` paginator and sorter.
  - `libs\js-shared\base-entity-frontend\src\lib\base-list\base-entity-list.component.ts:206` — its collection load supplies no page/sort state; subsequent data effects only copy the stored entity array.
  - `libs\js-shared\base-entity-frontend\src\lib\base-list\base-entity-list.component.html:59` — the paginator exposes local page-size options but has no server-total binding or page-request handler.
  - `libs\js-shared\base-entity-frontend\src\lib\base-query\base-entity-query.component.ts:88` — a server query replaces the same store data with the returned query result.
  - `libs\js-shared\base-entity-frontend\src\lib\base-toolbar\base-entity-toolbar.component.ts:104` — PDF export consumes the stored entity array rather than an explicitly defined query/export dataset.
  - `libs\js-shared\base-entity-frontend\src\lib\base-entity-store\base-entity.store.spec.ts:90` — the store test uses a response with 333 pages, while grid coverage at `libs\js-shared\base-entity-frontend\src\lib\base-list\base-entity-list.component.spec.ts:63` checks datasource setup, not retrieval of another server page.
- **Why it is debt:** The repository supports paged collections, but the list has only the semantics of a complete in-memory collection. Given ten content rows and a server total of one hundred, local paging/sorting operates on those ten and never requests the remaining ninety; server search and PDF export inherit the same partial dataset. This affects every generated feature using paginated services, rather than one bespoke table.
- **Suggested refactoring:** Introduce a shared query-state contract for page, size, sort and filter, and wire the grid to server totals and explicit page requests. Keep complete-array adapters as an explicit alternative instead of guessing from the current array length. Define export scope clearly—current page, selection or complete filtered query—and add integrated multi-page, sort/filter transition and export-scope tests.
- **Depends on / blocks:** none

### FEN-MET-02 — Field-level advisory violations become blocking Angular errors

- **Category:** MET
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\js-shared\base-entity-frontend\src\lib\rule-engine\rule-engine.ts:4` — the rule contract distinguishes ERROR, WARNING and INFO severities.
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.ts:46` — explicit rule blocking tests ERROR severity only.
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.ts:139` — every failing field-targeted result is applied through `control.setErrors`, including WARNING and INFO.
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.ts:66` — Save also disables on `baseEntityForm.invalid`, so those advisory control errors block the button.
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.html:8` — blur evaluates rules, making the blocking field state reachable during ordinary editing.
  - `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.component.spec.ts:180` — inspected field-level and submission tests exercise ERROR results, not advisory severities with fields.
- **Why it is debt:** Domain severity and Angular control validity are collapsed into one error channel. The same WARNING can remain advisory without a field target but disable Save when a field is attached, although the explicit rule gate still says only ERROR is blocking. Metadata that should control feedback placement therefore changes whether users can persist a record.
- **Suggested refactoring:** Separate advisory field messages from blocking validation errors and apply Angular errors only for blocking outcomes. Derive button and submit-handler eligibility from the same validation policy. Test WARNING/INFO with and without fields, mixed severities, correction after blur and preservation of ordinary required/pattern validators.
- **Depends on / blocks:** none

## Out-of-scope observations

- Backend enforcement, API defaults, host authorization and other libraries' route/DI configuration were not inspected. The pagination finding is conditional on the explicitly supported paged response shape, not an assertion about a particular server's current dataset.
- FRU-API-01 independently concerns whether a successful rule load contains the full context; FEN-MET-01 concerns when this form treats that load as ready. The fixes address different boundaries and should not be merged into one finding.
- Public generic service types and shared singleton stores were inspected as context; no additional finding is scored merely for framework types, root providers or RxJS usage without a traced consequence.

## Strengths worth keeping

- The rule engine is a public injected port rather than a direct dependency on a particular evaluator: `libs\js-shared\base-entity-frontend\src\lib\rule-engine\rule-engine.ts:26`.
- Generic query loading uses `switchMap` to cancel superseded reads: `libs\js-shared\base-entity-frontend\src\lib\base-entity-store\findByQuery.ts:17`; this is a useful race-control boundary, separate from rule-readiness debt.
- Form construction dispatches by metadata and supports both nested layout and extension controls: `libs\js-shared\base-entity-frontend\src\lib\base-form\base-entity-form.builder.ts:34` and line 73.
- Descriptor synthesis is a pure definition-plus-lookup transformation rather than a service lookup hidden inside rendering: `libs\js-shared\base-entity-frontend\src\lib\base-entity-definition\dynamic-entity.descriptor.ts:91`.
- PDF column selection shares descriptor flattening and hidden-column policy with the grid, and uses explicit date-format metadata: `libs\js-shared\base-entity-frontend\src\lib\pdf-service\entity-descriptor-to-pdf-columns.ts:21`. This common policy is worth preserving while dataset scope is corrected.

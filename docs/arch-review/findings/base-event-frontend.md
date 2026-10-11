# Architecture review: base-event-frontend

- **Slice code:** FEV
- **Paths in scope:** `libs\js-shared\base-event-frontend`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** Static review: scoped discovery, file reads and numbered PowerShell evidence checks. Inspected the model, mapper, descriptors, service/store/facade, provider/route registration, manifest/public exports and mapper/descriptor/HTTP/store tests. Git confirmed matching HEAD/scoped tracked source. No builds, lint, tests or browser execution.
- **Calibration:** Effort estimates are provisional; rubric section 9 calibration has not been performed.

## Summary

This is a small adapter over the generic entity framework, with a signal store and an explicit wire mapper rather than a separate bespoke form stack. The event catalog is exposed through a named facade registration, avoiding compile dependencies from consumers. One evidenced design debt remains: the form's declared fields/validity and the mapper's event-kind rules describe different editable models. No additional race or tenant-isolation finding is asserted from the inherited generic store, whose implementation is outside scope.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FEV-MET-01 | Event variants are normalized only after form editing | MET | 3 | 1 slice | M | High | 1.5 |

## Findings

### FEV-MET-01 — Event variants are normalized only after form editing

- **Category:** MET
- **Criticality:** 3
- **Blast radius:** 1 slice
- **Effort:** M
- **Confidence:** High
- **Priority score:** 1.5
- **Evidence:**
  - `libs\js-shared\base-event-frontend\src\lib\domain\event-definition.descriptors.ts:29` — kind is required, but all subject/action/state controls remain present and optional at lines 32–40.
  - `libs\js-shared\base-event-frontend\src\lib\domain\event-definition.mapper.ts:44` — serializer removes binding fields for MESSAGE/SIGNAL and removes state unless action is STATE_CHANGED.
  - `libs\js-shared\base-event-frontend\src\lib\domain\event-definition.descriptors.spec.ts:52` — explicitly records that SYSTEM-only requiredness cannot be expressed and leaves all subject fields optional.
  - `libs\js-shared\base-event-frontend\src\lib\domain\event-definition.mapper.spec.ts:36` — verifies populated binding fields are discarded for MESSAGE/SIGNAL rather than presenting that semantic change in the editor.
- **Why it is debt:** The generated form can appear valid while a SYSTEM binding lacks required information, or show editable values that serialization deliberately discards for another kind. The normalization is a documented defensive workaround, not an accidental missing spread, but it places the event variant's semantics solely at the transport boundary instead of in the metadata-driven editing contract. Users discover validity on submission and can lose entered binding values without the editor explaining the change.
- **Suggested refactoring:** Define one event-kind policy for applicable fields and validation, and connect it to the facade/editor as well as defensive serialization. Disable or hide inapplicable controls with an explicit kind-change policy, and require subject/action for SYSTEM while treating state as conditional on action. If the generic descriptor API cannot express this, add a narrowly scoped editor extension and coordinate a reusable conditional-metadata capability with its owner; test kind changes through the rendered form and emitted request.
- **Depends on / blocks:** none

## Out-of-scope observations

- `libs\js-shared\base-event-frontend\src\lib\domain\event-definition.store.ts:14` delegates CRUD/loading to `BaseEntityStore`; cancellation, page shape handling, version preservation through generic forms, and tenant switching inside that implementation were not traced. No speculative issue is scored against it.
- Organization selection is represented by the configured service root in `libs\js-shared\base-event-frontend\src\lib\domain\event-definition.service.ts:7`. Host recreation/invalidation policy and backend catalog validation were not inspected.
- Conditional descriptor support is an external coordination question; this report scores only the concrete form/serializer mismatch owned by this slice. Current package peers name Angular 22.

## Strengths worth keeping

- Stable cross-feature entity identity is registered through the host facade map, rather than importing consumers: `libs\js-shared\base-event-frontend\src\lib\base-event.providers.ts:17`.
- The mapper explicitly carries optimistic version and strips only defined inapplicable binding fields: `libs\js-shared\base-event-frontend\src\lib\domain\event-definition.mapper.ts:44`. HTTP update coverage checks the version-bearing payload at `libs\js-shared\base-event-frontend\src\lib\domain\event-definition.service.spec.ts:52`.
- The signal store is a thin composition of established entity features rather than another duplicate cache: `libs\js-shared\base-event-frontend\src\lib\domain\event-definition.store.ts:12`.
- Routes reuse the generic container with scoped facade/i18n registration: `libs\js-shared\base-event-frontend\src\lib\base-event.routes.ts:19`. Mapper, descriptor, route/provider and HTTP tests were inspected, not executed.

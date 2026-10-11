# Architecture review: base-widget-backend

- **Slice code:** BWI
- **Paths in scope:** `libs\java-shared\base-widget-backend`
- **Reviewed at commit:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`
- **Date:** 2026-10-10
- **Tools run:** Static review using IDE directory/source inspection, scoped ripgrep, PowerShell read-only source inspection and file reads. Read `AGENTS.md`, the rubric and relevant platform intent. Inspected CRUD tests, module declarations, persistence/repository mappings, REST mapping and default loading. Tests, builds, ArchUnit and Modulith verification were not executed; no module-verification or persistence integration test was present in the inspected slice test tree. No Nx configuration was needed or read.
- **Calibration:** Reviewer/owner calibration was not performed. Effort estimates are provisional; rubric scales and effort points are unchanged.

## Summary

The slice has a compact tenant-keyed widget catalog with an intentional distinction between type metadata and widget implementation/runtime behavior. CRUD is appropriately small, but it has no use-case authorization collaborator despite accepting caller-selected organization keys. Listing accepts filter and order parameters that never reach a query, and the published module interface exposes persistence types despite claiming those types remain internal. These are concrete debts in existing behavior and boundaries, not complaints about opaque props schemas or unfinished widget rendering.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BWI-SEC-01 | Tenant-scoped CRUD has no authorization boundary | SEC | 5 | 2 feature | M | High | 5.0 |
| BWI-API-01 | Listing silently discards filter and order criteria | API | 3 | 2 feature | M | High | 3.0 |
| BWI-ABS-01 | The public widget surface exposes persistence internals | ABS | 3 | 2 feature | M | High | 3.0 |

## Findings

### BWI-SEC-01 — Tenant-scoped CRUD has no authorization boundary

- **Category:** SEC
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\adapter\inbound\WidgetEndpoint.java:20` — the endpoint explicitly acknowledges that path `orgKey` is not authorization and that a policy port is still needed.
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\adapter\inbound\WidgetEndpoint.java:53` — get/create/update/publish/delete pass the path organization directly to CRUD.
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\usecase\WidgetDefinitionCrud.java:34` — the repository is the use-case service's only collaborator.
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\usecase\WidgetDefinitionCrud.java:40` — reads scope by supplied organization; writes at lines 52–89 validate/load/save/delete without a caller access or design check.
  - `libs\java-shared\base-widget-backend\src\test\java\com\processpuzzle\widget\usecase\WidgetDefinitionCrudTest.java:39` — tests construct CRUD using only a mocked repository; they cannot exercise denial by a supplied policy.
- **Why it is debt:** Tenant predicates partition rows but do not establish that the caller may choose that tenant or mutate its catalog. The implemented use-case surface has no enforcement point for that decision, so every host/alternate caller must recreate protection outside the feature. This is a documented intentional gap that is risky in an already implemented multi-tenant CRUD API, not a finding based on absent scaffold functionality; an external host may compensate, but that was not inspected.
- **Suggested refactoring:** Introduce a feature-owned access/design policy or explicitly consume the shared organization guard at every use-case entry. Require membership for reads and design permission for mutation/publication, with an explicit test-only permissive mode rather than accidental missing wiring. Add restrictive-policy tests for cross-organization reads/writes and same-organization non-designer writes, and ensure default loading uses an explicit trusted provisioning context.
- **Depends on / blocks:** none

### BWI-API-01 — Listing silently discards filter and order criteria

- **Category:** API
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\adapter\inbound\WidgetEndpoint.java:39` — `listWidgetDefinitions` accepts `where` and `order`, but delegates only organization plus page/size at line 41.
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\usecase\WidgetDefinitionCrud.java:41` — the listing use case has no filter argument and applies only the tenant specification.
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\domain\WidgetDefinition.java:22` — the model's stated design explicitly anticipates RSQL filtering and sorting on scalar catalog fields.
- **Why it is debt:** The implemented API accepts query criteria and returns success while ignoring their meaning. Catalog consumers cannot reliably implement metadata-driven filtering/sorting and may compensate client-side over only one page, yielding incomplete results. This conclusion follows the in-scope endpoint signature and call chain; the separate generated contract was not inspected.
- **Suggested refactoring:** Carry filter and sort criteria into an application-owned listing query, parse them through the established RSQL/sort abstraction and AND the user predicate with the mandatory tenant predicate. Add endpoint delegation tests plus persistence tests for filtering, deterministic ordering, pagination and tenant isolation. If an option is intentionally unsupported, reject it explicitly instead of silently discarding it.
- **Depends on / blocks:** none

### BWI-ABS-01 — The public widget surface exposes persistence internals

- **Category:** ABS
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\package-info.java:15` — the declared intent is to expose only the status enum through the domain interface and keep the entity/repository internal.
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\domain\package-info.java:6` — `@NamedInterface("domain")` is applied to the package that contains the public entity and repository, not a status-only API package.
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\domain\WidgetDefinitionRepository.java:15` — the public repository in that package extends Spring Data JPA interfaces.
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\usecase\package-info.java:7` — CRUD is an exposed named interface.
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\usecase\WidgetDefinitionCrud.java:41` — public operations return JPA `WidgetDefinition` instances or `Page<WidgetDefinition>`; the public constructor at line 36 also names its repository.
  - `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\domain\WidgetDefinition.java:191` — exposed entities offer mutable props/port setters, not just a read projection.
- **Why it is debt:** The named-interface placement exposes the package's public persistence types, and the use-case return types reinforce that coupling rather than hiding it. This contradicts the slice's explicit repository/entity encapsulation intent and permits consumers to depend on JPA details or mutable entity methods instead of a stable catalog view. The blast is feature-wide because narrowing the advertised surface requires checking dependent consumers, although none were inspected here.
- **Suggested refactoring:** Put only the intended public values/projections in a dedicated named-interface package and remove the broad domain export. Return application-owned immutable widget views from public queries/mutations; keep entity construction and Spring Data repositories internal. Add a Modulith boundary test that inspects the named-interface exports and rejects repository/entity exposure, alongside compatibility checks for consumers.
- **Depends on / blocks:** none

## Out-of-scope observations

- Host security filters, core guard implementation, generated OpenAPI source, Angular widget runtime and importing consumers were not inspected. Deployment exposure and current external uses of the public persistence types remain unscored.
- `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\package-info.java:5` names app/document containers as consumers by intent; this report does not claim their current call paths.

## Strengths worth keeping

- `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\package-info.java:19` limits outbound dependencies to core/shared and explicitly separates catalog metadata from Angular widget implementations.
- `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\usecase\WidgetDefinitionCrud.java:68` prevents key renaming, and line 123 centralizes rejection of ambiguous duplicate port names.
- `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\usecase\WidgetDefinitionCrud.java:143` receives an application-owned writable draft record rather than accepting an externally constructed JPA entity.
- `libs\java-shared\base-widget-backend\src\test\java\com\processpuzzle\widget\usecase\WidgetDefinitionCrudTest.java:137` pins the deliberate opaque-schema contract; it is not mistaken for missing backend JSON Schema validation in this review.
- `libs\java-shared\base-widget-backend\src\main\java\com\processpuzzle\widget\adapter\inbound\DefaultWidgetLoader.java:134` checks for existing definitions before creating defaults, preserving existing authored data across ordinary restarts.

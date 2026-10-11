# Architecture review: util

- **Slice code:** FUT
- **Paths in scope:** `libs\js-shared\util`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** Scoped source/test discovery and reads, ripgrep, resolved `npm exec nx -- show project util --json`. No tests, builds or lint executed.
- **Calibration:** Provisional effort estimates; rubric scales unchanged and owner calibration unperformed.

## Summary

The utility library centralizes service-root fallback, configuration merging, translations, locale routing and error reporting instead of duplicating those policies across features. Its main architectural debt is the boundary between a supported optional capability and a failed required capability. Configuration treats every missing/erroring source as optional, while translation loading converts every backend failure into a valid empty bundle. Both choices protect initial rendering but lose information needed for a reliable degraded-state and recovery lifecycle.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FUT-OPS-01 | Configuration has no required-source or validity contract | OPS | 3 | 3 platform | M | High | 4.5 |
| FUT-OPS-02 | Translation failures become successful empty bundles | OPS | 3 | 3 platform | M | High | 4.5 |

## Findings

### FUT-OPS-01 — Configuration has no required-source or validity contract

- **Category:** OPS
- **Criticality:** 3
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.5
- **Evidence:**
  - `libs\js-shared\util\src\lib\runtime-configuration\configuration.service.ts:162` — common configuration, stage configuration and optional overrides enter the same source list.
  - `libs\js-shared\util\src\lib\runtime-configuration\configuration.service.ts:92` — every unsuccessful HTTP response contributes an empty configuration, including server failures.
  - `libs\js-shared\util\src\lib\runtime-configuration\configuration.service.ts:99` — any parseable JSON value is asserted to be the generic configuration type without shape validation.
  - `libs\js-shared\util\src\lib\runtime-configuration\configuration.service.ts:46` — merging can finish successfully with an empty or partial result.
  - `libs\js-shared\util\src\lib\runtime-configuration\configuration.service.spec.ts:131` — a stage-source 503 is explicitly expected to resolve with only common configuration.
- **Why it is debt:** Optional override support and tolerance of SPA HTML fallbacks are legitimate, but the service cannot express that some settings must be successfully loaded and validated before dependent features start. A required stage source that fails is treated like an absent customization, and a parseable scalar/array is accepted as configuration, leaving callers to discover missing settings later. The warning list helps diagnosis but does not replace a typed required/degraded/ready contract in this platform-wide bootstrap mechanism.
- **Suggested refactoring:** Let the host designate required versus optional sources and validate the merged configuration against a host-owned runtime guard before exposing it as ready. Preserve the optional-file fallback while representing missing required data explicitly, with actionable diagnostics and a retry or configuration-error surface. Test missing common/stage sources, optional overrides, parseable wrong shapes, and recovery without silently substituting unrelated service roots.
- **Depends on / blocks:** none

### FUT-OPS-02 — Translation failures become successful empty bundles

- **Category:** OPS
- **Criticality:** 3
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.5
- **Evidence:**
  - `libs\js-shared\util\src\lib\transloco\transloco.loader.ts:38` — any asset failure falls through to the owning backend.
  - `libs\js-shared\util\src\lib\transloco\transloco.loader.ts:75` — an intentionally absent backend resolves to an empty bundle.
  - `libs\js-shared\util\src\lib\transloco\transloco.loader.ts:80` — a configured backend's failed response resolves to the identical success-shaped empty bundle.
  - `libs\js-shared\util\src\lib\transloco\transloco.loader.ts:61` — documentation explicitly states that this avoids Transloco failures/retries and makes backend absence indistinguishable from failure.
  - `libs\js-shared\util\src\lib\transloco\transloco.loader.spec.ts:104` — the configured-backend 500 test asserts `{}` rather than an observable degraded-load status.
- **Why it is debt:** Keeping a component tree alive is useful, but the loader discards the distinction between a scope with no translations and one whose translations could not be obtained. Dynamic metadata-authored scopes have no shipped asset fallback, so a transient outage becomes missing UI vocabulary without a retry signal at this boundary. A host HTTP interceptor may log the failure, but cannot restore the loader's lost readiness/recovery semantics; the effect spans every feature using this shared loader.
- **Suggested refactoring:** Keep rendering-safe fallback but track failed scope/locale loads separately from successful empty bundles, exposing diagnostics and a bounded retry/invalidation path. Treat intentionally absent backends and genuine not-found bundles according to an explicit policy rather than treating all statuses identically. Add tests with the real Transloco service for failure then recovery, ensuring fallback-language handling neither crashes the tree nor permanently treats the failure as loaded content.
- **Depends on / blocks:** none

## Out-of-scope observations

- Host configuration requirements, interceptor registration and production deployment policies were not assumed. The findings concern the shared utility contracts and their inspected tests.
- The root Nx dependency rule uses wildcard tag constraints; this was read as workspace context, not scored as a forbidden dependency inside util.

## Strengths worth keeping

- `libs\js-shared\util\src\lib\runtime-configuration\service-root.ts:22` centralizes feature-root fallback instead of making feature adapters independently interpret deployment topology.
- `libs\js-shared\util\src\lib\transloco\transloco.loader.ts:25` accepts a feature-contributed translation-source registry, enabling runtime scopes without depending on feature internals.
- `libs\js-shared\util\src\lib\layout-service\layout.service.ts:34` returns an idempotent release function for counted sidenav visibility requests.
- `libs\js-shared\util\src\lib\error-handler\central-http-error.interceptor.ts:9` reports an HTTP failure while rethrowing it, preserving the caller's error semantics.

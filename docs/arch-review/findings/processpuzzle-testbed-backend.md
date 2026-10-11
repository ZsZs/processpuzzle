# Architecture review: processpuzzle-testbed-backend

- **Slice code:** PTB
- **Paths in scope:** `apps\processpuzzle-testbed-backend`
- **Reviewed at commit:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`
- **Date:** 2026-10-10
- **Tools run:** Source/configuration/POM/test reads and scoped ripgrep; `npm exec nx -- show project processpuzzle-testbed-backend --json` resolved the application targets. Root Maven reactor and architecture/deployment documents were read for context. No application, database, build or tests were run.
- **Calibration:** Effort estimates are provisional; owner/reviewer calibration has not been performed and scales are unchanged.

## Summary

The testbed is a genuine composition root: it supplies cross-feature adapters and hosts the complete Modulith verification test. Durable publication is explicitly configured without reintroducing the expensive runtime module scan. However, the AI lifecycle bridge still converts failed delivery into successful listener completion, contradicting that durable-delivery model. The database also relies on startup schema mutation without a versioned data-evolution path; both issues concern implemented lifecycle responsibilities rather than missing feature scaffolds.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PTB-OPS-01 | The AI lifecycle bridge acknowledges failed delivery | OPS | 4 | 2 feature | M | High | 4.0 |
| PTB-OPS-02 | Persistent schemas evolve through startup mutation only | OPS | 4 | 3 platform | L | High | 3.0 |

## Findings

### PTB-OPS-01 — The AI lifecycle bridge acknowledges failed delivery

- **Category:** OPS
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `apps\processpuzzle-testbed-backend\src\main\java\com\processpuzzle\composition\AiSubjectChangeListener.java:45` — the transactional deletion listener delegates to gallery cleanup.
  - `apps\processpuzzle-testbed-backend\src\main\java\com\processpuzzle\composition\AiSubjectChangeListener.java:49` — any runtime failure is logged and swallowed; changed-subject delivery uses the same pattern at line 57.
  - `apps\processpuzzle-testbed-backend\src\main\resources\application.yaml:35` — outstanding publications are replayed on restart, but completion mode at line 38 deletes successfully completed publications.
  - `apps\processpuzzle-testbed-backend\src\test\java\com\processpuzzle\composition\AiSubjectChangeListenerTest.java:45` — the failure test explicitly requires every failed callback, including deletion, to return normally.
- **Why it is debt:** The bridge prevents failures from reaching the publisher, but also hides them from the publication registry that distinguishes failed delivery from completion. A gallery deletion that throws is acknowledged without a durable retry outcome at this boundary; changed subjects may converge on a later recognition, but deletion cannot rely on that repair path. This is a conflict between two explicit lifecycle designs in the application, not proof that every downstream cleanup failure behaves identically.
- **Suggested refactoring:** Make delivery success explicit: either let failure leave the publication incomplete or persist a retryable gallery command before acknowledging it. Preserve idempotency, separate the original entity transaction from retry execution, and test a committed delete with a failing gallery followed by successful redelivery. Coordinate this bridge change with the downstream cleanup lifecycle rather than assuming a listener retry alone fixes every internal side effect.
- **Depends on / blocks:** none

### PTB-OPS-02 — Persistent schemas evolve through startup mutation only

- **Category:** OPS
- **Criticality:** 4
- **Blast radius:** 3 platform
- **Effort:** L
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `apps\processpuzzle-testbed-backend\src\main\resources\application.yaml:19` — explicitly states there is no migration tool and that Hibernate owns the schema.
  - `apps\processpuzzle-testbed-backend\src\main\resources\application.yaml:23` — selects `ddl-auto: update` for the persistent PostgreSQL database.
  - `apps\processpuzzle-testbed-backend\src\main\resources\application.yaml:30` — applies the same schema lifecycle to the event publication registry.
  - `apps\processpuzzle-testbed-backend\src\main\java\com\processpuzzle\ProcessPuzzleTestbedBackendApplication.java:26` — the application scans all feature entities and the registry into the same persistence unit.
  - `apps\processpuzzle-testbed-backend\pom.xml:23` — the application composes the independently versioned feature libraries in one deployed backend.
- **Why it is debt:** Automatic additive schema reconciliation is not a versioned upgrade strategy for renames, backfills, constraint changes or data transformations. As feature libraries evolve, the host has no explicit record or ordering of those transitions, including transitions involving persisted event payloads and newly introduced lock columns. Platform blast follows from this application's database hosting many features; the finding does not assume that private applications use identical configuration.
- **Suggested refactoring:** Baseline existing installations without destroying data, give each feature ownership of uniquely identified migrations, and let the host run a deterministic combined sequence before serving traffic. Move nondevelopment deployments to schema validation after migrations and define compatible rolling-upgrade/rollback limits. Add PostgreSQL upgrade tests starting from a populated prior-version database, including outstanding events; preserve a separate disposable development mode if useful.
- **Depends on / blocks:** none

## Out-of-scope observations

- Private application deployments, external database administration and infrastructure overrides were not audited; the scored schema finding is grounded in this host's shipped configuration.
- `apps\processpuzzle-testbed-backend\src\main\java\com\processpuzzle\composition\AiPortsConfiguration.java:52` documents a broad entity attribute-kind projection and implements media URL adaptation. Those feature-owned contracts should be coordinated with their slice owners; they are not independently scored here.
- No live endpoint security assessment was performed. Demonstration defaults are documented in `apps\processpuzzle-testbed-backend\src\main\resources\application.yaml:164` and are not treated as proof of internet exposure.

## Strengths worth keeping

- `apps\processpuzzle-testbed-backend\src\main\java\com\processpuzzle\composition\BaseAppPortsConfiguration.java:13` and `apps\processpuzzle-testbed-backend\src\main\java\com\processpuzzle\composition\StateAttributeAdapter.java:9` place knowledge of co-deployed features at the composition boundary.
- `apps\processpuzzle-testbed-backend\src\test\java\com\processpuzzle\ModularityTests.java:19` verifies the complete module graph at build time; its presence is confirmed, not its current execution result.
- `apps\processpuzzle-testbed-backend\src\test\java\com\processpuzzle\EventPublicationRegistryTest.java:33` exercises a realistic large event payload and completed-publication deletion.
- `apps\processpuzzle-testbed-backend\src\main\resources\application.yaml:24` disables open-session-in-view, avoiding implicit persistence access during response rendering.

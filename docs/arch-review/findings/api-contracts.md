# Architecture review: api-contracts

- **Slice code:** API
- **Paths in scope:** `libs\java-shared\api-contracts`
- **Reviewed at commit:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`
- **Date:** 2026-10-10
- **Tools run:** Scoped file inventory, YAML/POM/event-test reads and ripgrep. `npm exec nx -- show project api-contracts --json` resolved the build/test/lint commands successfully. No generator, build, contract validation or tests were executed.
- **Calibration:** Owner/reviewer calibration is unperformed; effort is provisional and scales are unchanged.

## Summary

The contracts are centralized and generated, with useful explicit mappings to shared Java models and a backward-compatibility fixture for persisted events. The centralization does not yet provide an executable contract-quality boundary: generator validation is disabled and shared schema copies rely on manual synchronization. These are two distinct safeguards: specification validity and equality of intentionally duplicated shapes. Neither finding asserts an observed malformed specification or current mismatch; both record evidenced architectural test gaps in a shared foundation.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| API-TST-01 | Contract generation skips specification validation | TST | 3 | 3 platform | M | High | 4.5 |
| API-DUP-01 | Canonical schema copies have no enforced parity | DUP | 3 | 3 platform | M | High | 4.5 |

## Findings

### API-TST-01 — Contract generation skips specification validation

- **Category:** TST
- **Criticality:** 3
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.5
- **Evidence:**
  - `libs\java-shared\api-contracts\pom.xml:86` — the shared generation execution sets `skipValidateSpec` to true; the storage execution repeats it at line 107 and subsequent executions use the same setting.
  - `libs\java-shared\api-contracts\pom.xml:79` — the plugin generates model/API code; no independent specification-validation execution accompanies it.
  - `libs\java-shared\api-contracts\src\test\java\com\processpuzzle\shared\event\EventJsonRoundTripTest.java:22` — the slice's test class checks event JSON compatibility rather than OpenAPI validity.
  - Resolved Nx output: `api-contracts:lint` runs `mvn -f libs/java-shared/api-contracts/pom.xml clean validate`; `api-contracts:build` runs `mvn -pl libs/java-shared/api-contracts -am clean install`. Neither target adds an explicit OpenAPI validation command.
- **Why it is debt:** Successful Java generation is not proof that an OpenAPI document is a valid and consumable contract, and the generator's available validation gate is explicitly disabled. The inspected slice supplies event serialization tests but no replacement schema-validation gate, leaving contract defects to downstream tooling or consumers. The blast is platform-wide because this artifact carries the contracts for many separately versioned features; no particular invalid document is claimed.
- **Suggested refactoring:** Establish a dedicated contract-validation phase for every generated specification and make it part of the normal Nx-backed validation path. Enable generator validation where supported, or use an equivalent validator with narrowly documented exceptions for demonstrated tooling limitations. Verify validation fails on a deliberately invalid fixture and remains compatible with Windows paths before removing any workarounds.
- **Depends on / blocks:** none

### API-DUP-01 — Canonical schema copies have no enforced parity

- **Category:** DUP
- **Criticality:** 3
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.5
- **Evidence:**
  - `libs\java-shared\api-contracts\src\main\resources\shared-api.yaml:149` — the canonical widget schema explicitly requires every consuming specification to repeat its shape; cross-file references were avoided after Windows generator failures.
  - `libs\java-shared\api-contracts\src\main\resources\base-app-api.yaml:754` — repeats `WidgetInstance`, including a manual instruction to keep the property set identical.
  - `libs\java-shared\api-contracts\src\main\resources\shared-api.yaml:260` — declares canonical `InputPort`; `libs\java-shared\api-contracts\src\main\resources\base-widget-api.yaml:413` repeats it.
  - `libs\java-shared\api-contracts\src\main\resources\base-document-api.yaml:1494` — generates a separate `DocumentInputPort` from the equivalent shape.
  - `libs\java-shared\api-contracts\pom.xml:138` — schema mappings reuse Java types but do not compare source YAML declarations.
  - The scoped `src\test` inventory contains `EventJsonRoundTripTest.java` and its profile configuration, with no schema-parity test.
- **Why it is debt:** The Windows workaround is explicit and reasonable, but type mapping does not enforce agreement between independently edited source schemas. Changes to a shared widget or port therefore require coordinated edits across specifications, while separate client generators still consume the duplicated YAML. This is harmful maintenance duplication spanning app, document and widget contracts, not a claim that their currently inspected copies already disagree.
- **Suggested refactoring:** Add structural parity tests that compare the declared canonical/repeated pairs while ignoring documentation text and allowing only explicitly listed differences. Include requiredness, nullability, enums, defaults and nested properties, not just property names. Keep the Windows workaround until a separately verified bundled-reference generation path can replace it; treat convergence of document-specific generated port names as a compatibility change, not a prerequisite for the parity tests.
- **Depends on / blocks:** none

## Out-of-scope observations

- Runtime endpoint conformance, frontend code generation and downstream CI validation outside the slice were not independently traced; these findings describe the contract artifact's own gates.
- The Maven parent supplies shared plugin management. Its broader release architecture is context, not a separately scored slice in this review.

## Strengths worth keeping

- `libs\java-shared\api-contracts\pom.xml:237` maps shared models explicitly instead of making every feature own incompatible Java copies.
- `libs\java-shared\api-contracts\src\test\java\com\processpuzzle\shared\event\EventJsonRoundTripTest.java:43` preserves a legacy persisted-event fixture, a useful pattern for future durable-event evolution.
- `libs\java-shared\api-contracts\pom.xml:261` documents the Jackson 2/3 boundary and maps opaque document content to a transport-neutral object tree rather than leaking a Jackson-specific node onto the wire.

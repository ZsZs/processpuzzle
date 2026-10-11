# Architecture review: base-rule-backend

- **Slice code:** BRU
- **Paths in scope:** `libs\java-shared\base-rule-backend`
- **Reviewed at commit:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`
- **Date:** 2026-10-10
- **Tools run:** Static review using IDE file discovery/read, scoped glob/ripgrep, numbered PowerShell reads, and Git revision verification. Read `AGENTS.md`, `README.md` architectural intent and the review rubric. Inspected rule engine, evaluation, CRUD/import synchronization, persistence model, module declarations and relevant tests. Tests inspected, not executed; no build, Nx task, ArchUnit or Modulith verification executed.
- **Estimation note:** Effort is provisional: rubric calibration has not been completed. The rubric's scales and effort points are unchanged.

## Remediation log

### BRU-MET-01: addressed (2026-10-11)

Each thread-local compiled function now retains the immutable source identity it was compiled
from. Evaluation replaces a cached function when the currently registered source differs, including
same-key edits and remove/re-register transitions. Tenant-scoped keys are unchanged. An in-flight
evaluation may finish on its captured source; no cross-thread GraalJS values are shared.

Five real-engine regressions reproduced the original failure before the fix: same-thread edits,
remove/re-create, a worker missing the removal interval, two warmed workers with tenant isolation,
and an invalid edit that must fail rather than execute the old function. BRU-PER-01 remains a
separate transaction-boundary change.

**Validation:** all 15 `RuleEngineTest` cases passed through `base-rule-backend:test` via Nx.
The five new cases failed against the original implementation before passing with the fix.

### BRU-PER-01: addressed (2026-10-11)

Create, update and delete now use the same after-commit synchronization as imports. Registration
callbacks capture immutable tenant-scoped key, expression and enabled state rather than retaining
managed entities; rollback and failed commit-time flushes do not publish the attempted change.

Evaluation also uses the expression from the row it actually read, compiling that snapshot without
publishing a shared registration. This closes the lazy-registration path that could otherwise
publish a caller's uncommitted row, and prevents a concurrent/stale registration from supplying an
expression inconsistent with the row's violation metadata. Registered-key evaluation remains
available for explicit engine use. Callback delivery is still local/in-process, not a durable or
cross-replica synchronization protocol.

**Validation:** 88 focused tests passed through Nx `base-rule-backend:test`, including
`RuleModularityTests`, the engine, CRUD, evaluation, import, tenant isolation/cleanup and
synchronization tests. Sixteen new transaction cases use a real H2/JPA repository, proxied use cases
and GraalJS: committed and rolled-back create/update/delete, disable/re-enable, failed commit-time
flushes, import/replacement-import rollback, warmed worker-thread evaluation and transaction-local
snapshots. The mutable-callback and transaction regressions reproduced failures before the fix.
No live PostgreSQL, process-crash or multi-replica test was performed.

The assessment and evidence below are the original review baseline.

## Summary (review baseline)

The slice has a useful organization-scoped evaluation boundary and keeps engine implementation packages internal. The substantive debt is in consistency between persisted rule metadata and executable in-memory state, rather than the intentionally unfinished inheritance model. BRU-MET-01 makes an already-evaluated rule continue using its old expression after an edit, even on a single thread. BRU-PER-01 lets ordinary CRUD publish executable changes before the database transaction commits, although a commit-aware mechanism already exists for imports. The inspected tests cover expressions, tenant separation and synchronization helpers separately, but do not establish a transaction-to-real-engine update contract.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| BRU-MET-01 | Compiled rule functions outlive the metadata revision | MET | 5 | 2 feature | M | High | 5.0 | Addressed 2026-10-11 |
| BRU-PER-01 | CRUD changes executable state before commit | PER | 5 | 2 feature | M | High | 5.0 | Addressed 2026-10-11 |

## Findings

### BRU-MET-01 — Compiled rule functions outlive the metadata revision

- **Category:** MET
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\engine\RuleEngine.java:77` — registration replaces the central `Source` for a key, without invalidating compiled functions.
  - `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\engine\RuleEngine.java:90` — evaluation obtains the latest source but `computeIfAbsent(key, ...)` reuses the thread's existing function; the key does not identify an expression revision.
  - `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\engine\RuleEngine.java:111` — unregistering removes only the central source, so removing and re-registering a key does not evict its compiled function either.
  - `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\UpdateRule.java:36` — an ordinary edit changes the stored expression and invokes engine registration.
  - `libs\java-shared\base-rule-backend\src\test\java\com\processpuzzle\rule\usecase\engine\RuleEngineTest.java:19` — each test creates a fresh engine; the registration/removal cases at lines 140–169 do not evaluate an edited or re-created same-key rule.
- **Why it is debt:** Runtime interpretation of persisted rules is the feature's core contract, but its two cache tiers have no shared revision identity. Registering `true`, evaluating it, registering `false` under the same key, and evaluating again on that thread follows the existing cached `true` function; a new thread can instead compile `false`. Consequently authored constraints can remain ineffective and verdicts can vary by request thread, affecting rule consumers as well as the authoring feature.
- **Suggested refactoring:** Store an immutable revision or source identity with each compiled function and replace the thread-local entry when it differs from the registered source. Apply the same rule to disable/re-enable and delete/re-create transitions. Add real-engine same-thread and two-thread tests that evaluate before and after a committed expression edit, including re-registration under the same tenant-scoped key.
- **Depends on / blocks:** none

### BRU-PER-01 — CRUD changes executable state before commit

- **Category:** PER
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\UpdateRule.java:13` — the use case is transactional, but lines 38–39 save and immediately register the changed rule.
  - `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\CreateRule.java:40` — creation also registers immediately after repository save.
  - `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\DeleteRule.java:38` — deletion immediately unregisters before the transaction has completed.
  - `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\EvaluateObject.java:37` — evaluation reads committed rule rows but only registers their expression when the key is absent, not when an existing cached expression differs.
  - `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\service\RuleEngineSync.java:47` — commit-aware registration already exists; `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\ImportRules.java:101` uses it.
  - `libs\java-shared\base-rule-backend\src\test\java\com\processpuzzle\rule\usecase\UpdateRuleTest.java:29` — repository and engine synchronization are mocked; line 51 explicitly verifies immediate registration.
  - `libs\java-shared\base-rule-backend\src\test\java\com\processpuzzle\rule\usecase\service\RuleEngineSyncTest.java:59` — rollback suppression is tested for the deferred helper, not for ordinary CRUD with a real transactional repository and engine.
- **Why it is debt:** The database and executable rule state are updated at different consistency boundaries. If an update registers a changed expression and its transaction subsequently rolls back, an evaluating thread without a compiled entry can execute that uncommitted expression while `EvaluateObject` reads the old persisted row and declines to refresh an already-registered key. Thus a failed rule edit can change validation behavior, independently of the stale-function defect in BRU-MET-01.
- **Suggested refactoring:** Route all CRUD synchronization through the existing after-commit path, capturing immutable rule state for the callback rather than a mutable managed entity. Add transaction-level tests for failed flush, explicit rollback and concurrent evaluation, using the real engine and verifying the effective expression rather than only mock calls. Keep import and ordinary CRUD on one synchronization policy.
- **Depends on / blocks:** none

## Out-of-scope observations

- None scored or independently investigated. Application-side rule adapters, event consumers, frontend interpretation and deployment replica behavior were outside the source scope; no claim is made about their wiring.
- Rule inheritance resolution is explicitly deferred in `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\service\RuleEngineSync.java:14`; intentional scaffold absence alone is not a finding.

## Strengths worth keeping

- Tenant identity is present at the persistence and executable-cache boundaries: `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\domain\RuleDefinition.java:29` includes organization in the composite identity, and `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\EvaluateObject.java:37` scopes both lookup and engine key.
- Evaluation errors are returned as ERROR violations rather than successful verdicts: `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\EvaluateObject.java:47`.
- The engine explicitly restricts host access, I/O and thread/process creation, and exposes entity maps through a dedicated proxy: `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\engine\RuleEngine.java:58` and `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\engine\EntityProxy.java:27`. This is a useful isolation structure, not a claim of a completed security assessment.
- Module dependency declarations and executable boundary tests exist: `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\package-info.java:20` and `libs\java-shared\base-rule-backend\src\test\java\com\processpuzzle\rule\RuleModularityTests.java:22`. Nested engine/service packages are not propagated by the named interface at `libs\java-shared\base-rule-backend\src\main\java\com\processpuzzle\rule\usecase\package-info.java:8`.

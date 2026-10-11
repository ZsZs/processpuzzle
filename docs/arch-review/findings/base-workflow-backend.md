# Architecture review: base-workflow-backend

- **Slice code:** BWF
- **Paths in scope:** `libs\java-shared\base-workflow-backend`
- **Reviewed at commit:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`
- **Source provenance:** Workspace HEAD was `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`; scoped Git comparisons confirmed no tracked source differences between this slice, HEAD and the requested review commit.
- **Date:** 2026-10-10
- **Tools run:** Static review only: IDE file discovery/read and attempted call hierarchy (timed out), followed by scoped glob, ripgrep, and numbered PowerShell reads. Inspected module declarations, Maven dependencies, definition validation/replacement, execution aggregates, start/catch/timer/completion paths, HTTP adapter, and representative tests. No builds, tests, Modulith verification, or runtime concurrency experiments executed.
- **Calibration:** All effort estimates are provisional; rubric section 9 calibration has not been performed.

## Summary

The slice has a substantial metadata interpreter, explicit host-facing ports, and useful separation between authoring and execution. Its main weakness is consistency across independently committed work: MESSAGE consumption is not atomic by occurrence, parallel joins can miss their activation, and external tool effects precede durable completion. Runtime execution also consults mutable definitions despite the documented snapshot guarantee. Unit tests explain many local decisions, but their mocked repositories and activation services do not establish concurrent transaction behavior. The findings concern implemented paths rather than the README's intentionally unfinished features.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BWF-PER-01 | MESSAGE consumption is not atomic by occurrence | PER | 5 | 2 feature | M | High | 5.0 |
| BWF-PER-02 | Parallel joins are evaluated before serialization | PER | 4 | 2 feature | M | High | 4.0 |
| BWF-ABS-01 | Tool effects have no durable execution boundary | ABS | 5 | 2 feature | L | High | 2.5 |
| BWF-MET-01 | Running instances are not definition snapshots | MET | 4 | 2 feature | L | High | 2.0 |
| BWF-OPS-01 | Automatic starts have no durable delivery outcome | OPS | 4 | 2 feature | L | High | 2.0 |

## Findings

### BWF-PER-01 — MESSAGE consumption is not atomic by occurrence

- **Category:** PER
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\adapters\inbound\IntermediateCatchListener.java:91` — checks prior occurrence delivery once, before iterating recipients; lines 102–110 advance to another recipient after an optimistic-lock failure.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\OccurCatchEventUseCase.java:55` — each recipient is committed in its own transaction; admission checks the recipient's state, not exclusive ownership of the occurrence.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\domain\EventInstance.java:53` — the occurrence index is non-unique, and the optimistic version at line 126 protects an individual event row.
  - `libs\java-shared\base-workflow-backend\src\test\java\com\processpuzzle\workflow\execution\adapters\inbound\IntermediateCatchListenerTest.java:113` — the race test mocks a lost recipient and verifies trying the next; it does not distinguish another message from another delivery of the same occurrence.
- **Why it is debt:** Two concurrent deliveries of the same occurrence can both pass the initial existence check. After one commits the first catch, the other can lose that catch and successfully commit the next, violating the promised single-recipient MESSAGE semantics. Row-level optimistic locking prevents duplicate writes to one catch, not duplicate business consumption across catches; the impact reaches all workflows using message correlation.
- **Suggested refactoring:** Persist a MESSAGE receipt uniquely keyed by organization and occurrence, claimed in the same transaction as recipient selection and occurrence. Distinguish an already-consumed occurrence from contention caused by a different message; retain broadcast semantics for SIGNAL and SYSTEM. Add real-transaction tests for concurrent identical occurrences, different messages competing for one catch, and replay after commit.
- **Depends on / blocks:** none

### BWF-PER-02 — Parallel joins are evaluated before serialization

- **Category:** PER
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\WorkflowProgression.java:49` — activates eligible tasks before acquiring the instance lock at line 51; the locked section only checks terminality.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\TaskActivationService.java:133` — reads task rows into the pass's local flow state; line 402 rejects an unsatisfied join.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\TaskActivationService.java:446` — terminality uses fresh counts, but does not reactivate pending successors.
  - `libs\java-shared\base-workflow-backend\src\test\java\com\processpuzzle\workflow\execution\usecases\inbound\WorkflowExecutionUseCasesTest.java:370` — explicitly substitutes interaction assertions for concurrent transactions; the activation service is mocked at line 90.
- **Why it is debt:** If parallel tasks A and B complete concurrently and C depends on both, each pre-lock activation can see the other predecessor still ACTIVE and leave C PENDING. The subsequent serialized terminality check correctly sees C as non-terminal, but neither transaction repeats activation, leaving an otherwise ready workflow stalled. Fresh terminal counts solve close-out visibility, not join visibility; this undermines the advertised parallel execution model and its task-facing UI.
- **Suggested refactoring:** Serialize the progression decision and reload its task/event/context snapshot after acquiring the coordination lock, or persist a retryable progression command processed after predecessor commits. Avoid reusing managed entities loaded before the lock. Add a two-transaction A/B-to-C barrier test that asserts C becomes ACTIVE without another user action, together with parallel terminal-node coverage.
- **Depends on / blocks:** none

### BWF-ABS-01 — Tool effects have no durable execution boundary

- **Category:** ABS
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.5
- **Evidence:**
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\CompleteTaskUseCase.java:45` — completion is transactional; line 113 invokes tools before saving completion and progressing at lines 118–125.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\ToolStepExecutor.java:69` — performs the outbound invocation; failures become `StepResult.error` at line 75 rather than a task-level failure decision.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\adapters\outbound\RestToolInvocationAdapter.java:68` — sends real HTTP requests synchronously.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\outbound\ToolInvocationPort.java:22` — the invocation contract carries tool, operation and payload, but no execution identity/idempotency key.
- **Why it is debt:** A successful remote write cannot be rolled back if a later step, progression, flush, or competing completion makes the local transaction fail. Retrying completion can repeat an already-applied business action, while an ordinary failed HTTP result currently still allows the task to become COMPLETED. The missing durable step lifecycle, rather than the presence of an HTTP adapter, is the consistency gap affecting applications orchestrated by this feature.
- **Suggested refactoring:** Persist step execution intents and stable execution identities before dispatch, then record outcomes in separate transactions and resume progression only under an explicit success/failure policy. Pass an idempotency identity to capable tools and document compensation/manual reconciliation for non-idempotent endpoints. Test remote success followed by local rollback, duplicate completion, partial multi-step failure, and recovery after restart.
- **Depends on / blocks:** none

### BWF-MET-01 — Running instances are not definition snapshots

- **Category:** MET
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.0
- **Evidence:**
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\domain\WorkflowInstance.java:45` — promises execution under the starting definition version, but stores workflow identity/name, not a revision or resolved definition snapshot.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\CompleteTaskUseCase.java:90` — resolves the current workflow and current task definition for completion.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\TaskActivationService.java:160` — reconciles newly added events into an existing run; line 446 counts terminality using the current definition's event IDs.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\definition\usecases\inbound\ReplaceWorkflowUseCase.java:45` — replaces live content without pinning or migrating running instances.
- **Why it is debt:** Copying task IDs and display names does not snapshot executable semantics: later catalog edits change postconditions, steps, dependencies and event behavior for existing runs. Removing a task from the live definition can make its existing task impossible to complete, while removing an event can make terminality ignore it. This directly contradicts the documented execution guarantee and makes authoring changes operationally unsafe.
- **Suggested refactoring:** Persist an immutable resolved execution revision including task/tool behavior, topology and event/timer configuration, and bind each instance to it. Resolve all runtime paths from that revision; retain editable draft definitions for future runs. Define an explicit migration/adoption operation for existing instances and a policy for pre-revision data, with edit-during-execution tests.
- **Depends on / blocks:** none

### BWF-OPS-01 — Automatic starts have no durable delivery outcome

- **Category:** OPS
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.0
- **Evidence:**
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\adapters\inbound\TriggeredStartListener.java:65` — passes subject/context but not occurrence identity to the start use case; lines 74–77 catch and log each failed start without failing the listener.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\StartWorkflowInstanceUseCase.java:107` — starts in an independent transaction and deduplicates only by existence of a non-terminal instance for the subject.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\domain\WorkflowInstance.java:58` — the declared unique constraint covers organization/instance number, not source occurrence.
  - `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\InstanceNumberAllocator.java:32` — numbering serializes later in instance creation, not the preceding delivery/admission check.
- **Why it is debt:** A failed independent start is logged while its parent listener can complete, so registry durability does not preserve that workflow's unfinished work. Conversely, replay after a prior run becomes terminal can start it again, and two deliveries can pass the active-instance check before numbering serializes their inserts. Subject-based coalescing may be a valid business policy, but it is not an occurrence-based retry protocol; conflating them loses or duplicates automatic runs.
- **Suggested refactoring:** Record one durable start request/outcome per organization, workflow/start event and occurrence, with an atomic claim and explicit retryable/rejected/coalesced/completed outcomes. Keep subject coalescing as a separate configurable admission policy. Preserve per-workflow failure isolation while retrying unfinished requests, and test failure of one subscriber, concurrent delivery and replay after a completed run.
- **Depends on / blocks:** BWF-PER-01 — establish compatible receipt/occurrence conventions first; the start and catch implementations can otherwise be developed independently.

## Out-of-scope observations

- Unscored coordination: `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\usecase\DeleteEventDefinition.java:23` permits removal of a named event. Its lifecycle protection is owned by BEV-MET-01, not duplicated here.
- Host publication-registry configuration, generated API schemas, authorization adapters and frontend round-tripping were not inspected. No claim is made that deployment-wide retries or security policies have been verified.

## Strengths worth keeping

- Explicit host extension surfaces: `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\definition\usecases\outbound\package-info.java:22` and `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\outbound\package-info.java:13` publish named ports rather than host-specific implementations.
- Independent runtime rows and per-task context contributions avoid rewriting a shared context for every completion: `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\CompleteTaskUseCase.java:98` and `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\CompleteTaskUseCase.java:115`. Preserve that separation while fixing progression coordination.
- Timer firing rechecks eligibility in an isolated transaction and injects a clock: `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\usecases\inbound\FireTimerUseCase.java:75`. The sweep retries failed due rows and bounds each pass: `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\execution\adapters\inbound\TimerSweep.java:62`.
- An executable module-boundary verification test exists at `libs\java-shared\base-workflow-backend\src\test\java\com\processpuzzle\workflow\WorkflowModularityTests.java:19`; it was inspected, not executed.

# Architecture review: base-state-backend

- **Slice code:** BST
- **Paths in scope:** `libs\java-shared\base-state-backend`
- **Reviewed at commit:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`
- **Source provenance:** Workspace HEAD was `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`; scoped Git comparisons confirmed no tracked source differences between this slice, HEAD and the requested review commit.
- **Date:** 2026-10-10
- **Tools run:** Static review only: scoped glob/ripgrep, file reads and numbered PowerShell reads. Inspected module/Maven boundaries, topology and persistence models, authoring mapper/update/import, state read/fire/start flows, gateway/action ports, initialization listener, consistency check and representative tests. No build, tests, Modulith verification or runtime experiments executed.
- **Calibration:** All effort estimates are provisional; rubric section 9 calibration has not been performed.

## Summary

The implementation is substantially beyond its scaffold README and has a clear state-operation entry point, metadata validator and substitutable entity gateway. The highest-priority issue is loss of stale authoring edits despite an entity-level optimistic version. Initialization does not reconcile an old creation event against current state, while action execution lacks an explicit external-effect boundary. Topology replacement also has no policy for objects already occupying states that an edit removes. These are lifecycle and consistency gaps in implemented behavior, not findings against intentionally absent features.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BST-PER-01 | Authoring replacement loses stale client edits | PER | 5 | 2 feature | M | High | 5.0 |
| BST-OPS-01 | Initialization is not replay-safe against current state | OPS | 4 | 2 feature | M | High | 4.0 |
| BST-ABS-01 | Transition actions precede the authoritative state write | ABS | 5 | 2 feature | L | Medium | 2.5 |
| BST-MET-01 | Topology edits do not protect governed objects | MET | 4 | 2 feature | L | High | 2.0 |

## Findings

### BST-PER-01 — Authoring replacement loses stale client edits

- **Category:** PER
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\adapter\inbound\StateEndpoint.java:101` — maps an input and invokes whole-document replacement with no expected revision.
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\adapter\inbound\StateMapper.java:36` — constructs the replacement topology without a client version.
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\UpdateStateMachineDefinition.java:27` — loads the latest row and replaces its topology; lines 10–12 explicitly rely on the freshly loaded Hibernate version.
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\domain\StateMachineDefinition.java:83` — the entity has `@Version`, but that cannot recover the revision the user originally edited.
- **Why it is debt:** Two users can load the same topology, save sequentially, and have the second overwrite the first's changes without a conflict. Hibernate correctly protects overlapping database transactions, but each later request loads the newest version and therefore loses the disconnected client's concurrency precondition. Because the overwritten document controls allowed transitions and guards, this is metadata integrity loss affecting both the authoring UI and live state behavior.
- **Suggested refactoring:** Carry an expected revision or ETag through the write contract, mapper and use case, and reject stale replacements before applying changes while retaining JPA optimistic locking. Define whether unconditional writes are allowed explicitly rather than accidentally. Add a read/read/write/write regression and an overlapping-transaction test; coordinate the contract change with its owner.
- **Depends on / blocks:** none

### BST-OPS-01 — Initialization is not replay-safe against current state

- **Category:** OPS
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\adapter\inbound\EntityObjectCreatedListener.java:53` — forwards the original creation payload/version on every delivery; line 59 catches all runtime failures and logs them.
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\StartStateMachine.java:69` — decides whether initialization is needed solely from that original payload; lines 75–76 always write using the event's version.
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\adapter\outbound\BaseEntityObjectGateway.java:64` — delegates a version-checked write and translates a version conflict.
  - `libs\java-shared\base-state-backend\src\test\java\com\processpuzzle\state\adapter\inbound\EntityObjectCreatedListenerTest.java:55` — tests swallowed failure through a mocked use case, not successful replay or conflict recovery.
- **Why it is debt:** After any intervening update, repeating the same creation event cannot initialize an unset state because its expected version remains permanently stale. Replaying an already successful initialization likewise attempts the old write instead of recognizing current state as complete. Catching the exception does not repair this protocol: depending on rollback-only propagation it can be logged or fail at transaction completion, but neither outcome makes replay convergent; entity readers and event-driven consumers can consequently disagree about initialization.
- **Suggested refactoring:** Re-read the current object through the gateway; treat an already initialized state as an idempotent no-op, and initialize an unset state using the current version with bounded conflict retry. Define terminal handling for deleted objects and retryable handling for transient failures, optionally with a durable receipt. Test duplicate delivery after commit and an intervening non-state update using real transactions.
- **Depends on / blocks:** none

### BST-ABS-01 — Transition actions precede the authoritative state write

- **Category:** ABS
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** Medium
- **Priority score:** 2.5
- **Evidence:**
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\port\TransitionAction.java:4` — explicitly permits side effects such as notifications; its only method is `execute(context)` with no delivery identity or transactional-effects contract.
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\service\StateMachineEngine.java:90` — invokes all actions before returning a successful outcome.
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\FireStateTransition.java:66` — calls the engine before the version-checked state update at line 73.
  - `libs\java-shared\base-state-backend\src\test\java\com\processpuzzle\state\usecase\FireStateTransitionTest.java:53` — mocks the engine, so the write/action rollback relationship is not exercised.
- **Why it is debt:** The extension contract allows a notification or other non-transactional effect to occur before a concurrent state write is rejected or the transaction rolls back. A pre-read version check cannot prevent another transaction winning afterward, and a database transaction cannot undo an external effect. This is an evidenced gap in the SPI's semantics, not a claim that a particular host action already loses data; host action implementations were outside this review.
- **Suggested refactoring:** Separate transactional state mutations from external action intents. Persist intents with the successful state transition, dispatch them after commit through a retryable handler with stable action identity, and document ordering/idempotency or compensation requirements. Retain an explicitly constrained transactional-action SPI where necessary; add a competing-transition test asserting only the committed transition produces an externally visible action.
- **Depends on / blocks:** none

### BST-MET-01 — Topology edits do not protect governed objects

- **Category:** MET
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.0
- **Evidence:**
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\UpdateStateMachineDefinition.java:30` — validates only the proposed topology, then replaces states and the state attribute.
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\StateMachineTopologyValidator.java:51` — validates entity attribute existence/type and internal state/transition references, not existing object occupancy.
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\ImportStateMachineDefinitions.java:114` — upsert also replaces topology directly.
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\GetEntityObjectState.java:38` — always uses the current machine; lines 45–49 return no transitions for a state no longer declared.
  - `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\adapter\inbound\GovernedStateConsistencyCheck.java:69` — consistency checking runs at application readiness and reports discrepancies, rather than governing authoring changes.
- **Why it is debt:** A structurally valid edit can remove a state still occupied by objects, leaving those objects without any available transition. Changing the state attribute can instead reinterpret an existing object through the initial-state fallback, without a corresponding business transition. Runtime metadata editing therefore needs a compatibility/migration policy beyond syntactic topology validation; startup logging does not provide one.
- **Suggested refactoring:** Compare old and proposed topology and reject destructive changes while affected objects exist, using a narrow occupancy/query port. Alternatively bind objects to immutable machine revisions and provide an explicit migration with old-to-new state mapping. Apply the same policy to imports and coordinate metadata migration with version preconditions.
- **Depends on / blocks:** BST-PER-01 — revision-aware writes should precede reliable compatibility decisions.

## Out-of-scope observations

- Unscored integration limit: `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\adapter\outbound\BaseEntityObjectGateway.java:46` and `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\adapter\outbound\BaseEntityObjectGateway.java:64` forward organization identity into another feature's public API. That API's tenant isolation and atomic implementation were not inspected, so the adapter's stale comment about ignoring organization identity is not evidence of a current isolation defect.
- Shared/generated contracts, the host's publication registry, and external action implementations were not inspected. Contract and deployment changes mentioned above require coordination with their owners.

## Strengths worth keeping

- Clear operation-owned compare-and-swap plus event publication: `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\FireStateTransition.java:57` checks the caller's version, and `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\FireStateTransition.java:73` writes before publishing state-change facts.
- Metadata integrity is centralized: `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\usecase\StateMachineTopologyValidator.java:94` checks transition references, final-state rules, ambiguous triggers and registered guard/action beans.
- Domain collection reads are defensive, and JSON conversion is localized in the aggregate mapping: `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\domain\StateMachineDefinition.java:73` and `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\domain\StateMachineDefinition.java:165`.
- A narrow gateway adapter translates external exceptions into local port failures, and module verification exists: `libs\java-shared\base-state-backend\src\main\java\com\processpuzzle\state\adapter\outbound\BaseEntityObjectGateway.java:44`; `libs\java-shared\base-state-backend\src\test\java\com\processpuzzle\state\BaseStateModularityTests.java:15`. These structures were inspected, not executed.

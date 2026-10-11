# Architecture review: base-ai-backend

- **Slice code:** BAI
- **Paths in scope:** `libs\java-shared\base-ai-backend`
- **Reviewed at commit:** ada4dab69e96143cdfdab3d5724b58692c22eee1
- **Date:** 2026-10-10
- **Tools run:** Read-only IDE symbol lookup, glob/ripgrep, source/test/documentation reads, Git HEAD/status, and `npm exec nx show project base-ai-backend --json` (successful; resolved build/test/lint targets use Maven). No tests, builds, lint or Modulith verification executed.

## Summary

The feature has credible module-level hexagonal boundaries: external subjects, media and inference are ports, and generated vision-client models stay in the HTTP adapter. Internally it is a pragmatic Spring/JPA application rather than a persistence-independent domain: application services operate directly on domain-package JPA repositories and managed entities. The significant debts are non-durable external cleanup, competing job-state writers, and the dependence of durable retries on mutable/deletable profile metadata. The durable ticket/poller design is worth preserving, but it does not currently cover all the lifecycle guarantees around those tickets.

Effort estimates are provisional: rubric section 9 calibration has not been performed. Static review inspected `VisionJobsTest`, `TenantDataCleanerTest` and `AiModularityTests`; their presence is not a passing test result. In particular, job orchestration tests use mocked transactions and a synchronous executor, whereas tenant-cleanup tests exercise real H2 transactions. Production PostgreSQL isolation, object-store lifecycle configuration, remote-service idempotency implementation and host event delivery were not verified.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BAI-OPS-01 | External cleanup is forgotten after database deletion | OPS | 4 | 2 feature | M | High | 4.0 |
| BAI-PER-01 | Concurrent workers can overwrite terminal job state | PER | 4 | 2 feature | M | High | 4.0 |
| BAI-MET-01 | Retried jobs depend on mutable, deletable profiles | MET | 3 | 2 feature | M | High | 3.0 |

## Findings

### BAI-OPS-01 — External cleanup is forgotten after database deletion

- **Category:** OPS
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\adapter\inbound\TenantDataCleaner.java:77` — reads open job IDs and erases the tenant's rows, including its tickets, inside the deleting transaction.
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\adapter\inbound\TenantDataCleaner.java:89` — schedules storage deletion and remote job discard only in an in-memory `afterCommit` synchronization.
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\adapter\inbound\TenantDataCleaner.java:101` — storage failure is logged; no durable cleanup work is retained.
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\usecase\Enrollments.java:126` — subject deletion removes gallery rows before `discardCrops`; its storage failures are only logged at line 263.
  - `libs\java-shared\base-ai-backend\src\test\java\com\processpuzzle\ai\adapter\inbound\TenantDataCleanerTest.java:175` — the outage test asserts successful row erasure, not eventual remote cleanup after recovery.
- **Why it is debt:** The implementation correctly avoids deleting remote data before a database commit, but replaces transactional durability with a one-shot callback. A process stop after commit or an object-store outage leaves tenant media behind after both its rows and its retry handles have disappeared; subject-photo removal has the same problem for crops. This affects the feature's externally visible erasure lifecycle and consuming applications, not just internal logging.
- **Suggested refactoring:** Persist cleanup intents for tenant prefixes, removed crops and remote job IDs in the same transaction as removal, in records that survive tenant-row erasure. Process them with idempotent, retryable workers and observable completion/failure status; account for in-flight jobs before declaring prefix cleanup complete. Add outage/restart tests proving cleanup resumes after the original transaction and process are gone.
- **Depends on / blocks:** none

### BAI-PER-01 — Concurrent workers can overwrite terminal job state

- **Category:** PER
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\usecase\VisionJobs.java:120` — submission acknowledgement reads a ticket, checks `SUBMITTING` in memory and changes it to `SUBMITTED`.
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\usecase\VisionJobs.java:133` — preparation accepts any open ticket, issues another attempt/token and has no atomic worker claim.
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\usecase\VisionJobs.java:253` — callback/poller result application independently reads and updates the same ticket; failure does likewise at line 305.
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\domain\VisionJobTicket.java:30` — the persisted state machine has no optimistic version field; transition methods unconditionally assign status.
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\domain\VisionJobTicketRepository.java:12` — ordinary JPA reads and overdue queries provide neither locking nor compare-and-set transitions.
  - `libs\java-shared\base-ai-backend\src\test\java\com\processpuzzle\ai\usecase\VisionJobsTest.java:93` — orchestration is constructed with a mocked transaction manager and `Runnable::run`.
- **Why it is debt:** Short transactions are appropriate, but an in-memory status check is not a concurrency boundary. An acknowledgement transaction can read `SUBMITTING`, overlap a callback committing `COMPLETED`, then commit the stale ticket as `SUBMITTED`; overlapping dispatches can also replace each other's callback-token hash. The resulting retries or rejected callbacks affect recognition consumers, and remote job-ID idempotency does not protect the local state machine.
- **Suggested refactoring:** Introduce atomic persisted transitions with expected state/version and an attempt identity or bounded lease for dispatch. Keep remote calls outside database locks, and apply acknowledgements/results only to the attempt they belong to; terminal states must not be reopened by stale writers. Test acknowledgement/callback and poller/dispatch overlaps with separate real transactions and controlled barriers.
- **Depends on / blocks:** none

### BAI-MET-01 — Retried jobs depend on mutable, deletable profiles

- **Category:** MET
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\usecase\VisionJobs.java:175` — recognition submission reloads the current profile; detector, OCR and matching settings are reconstructed from it for every attempt.
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\usecase\VisionJobs.java:191` — the attempt count advances only after profile lookup and request preparation succeed.
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\usecase\RecognitionProfiles.java:88` — profile replacement is unrestricted by outstanding jobs.
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\usecase\RecognitionProfiles.java:97` — deletion checks enrollment photos only, not pending recognitions.
  - `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\usecase\VisionJobs.java:334` — poller preparation errors are logged without terminalizing the ticket.
- **Why it is debt:** Persisting a job identifier does not preserve the metadata needed to reproduce the requested work. With an unavailable vision server and candidates without enrollment photos, a recognition can be queued and its profile then legally deleted; each preparation fails before incrementing attempts, so the usual retry limit cannot settle that pending recognition. Editing thresholds or detector settings also changes the meaning of an already accepted job on resubmission, making results dependent on outage timing.
- **Suggested refactoring:** Define and persist a versioned job configuration snapshot when accepting work, while refreshing only expiring media URLs on retries. Alternatively, explicitly enforce profile lifecycle rules for outstanding jobs and terminally cancel incompatible work. Cover profile update/deletion during an outage and repeated preparation failures in lifecycle tests.
- **Depends on / blocks:** none

## Out-of-scope observations

- Host adapters and event listeners determine how subject changes reach `SubjectGalleries`; only the in-scope port/use-case side was reviewed. No claim is made that the host relay is durable or non-durable.
- Object-store retention/lifecycle rules and the vision server's implementation were outside scope; they might mitigate orphan retention but are not demonstrated recovery mechanisms in this library.
- No cross-module boundary violation was verified. This is not a whole-application Modulith verification or an API-contract comparison.

## Strengths worth keeping

- `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\package-info.java:17` restricts dependencies to core/shared and documents narrow ports/galleries interfaces rather than direct feature imports.
- `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\adapter\outbound\HttpVisionServer.java:58` maps local port requests to generated transport types at the adapter boundary.
- `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\usecase\Enrollments.java:91` commits gallery/ticket work before dispatch; `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\usecase\VisionJobs.java:324` provides a persisted polling fallback instead of relying solely on callbacks.
- `libs\java-shared\base-ai-backend\src\main\java\com\processpuzzle\ai\usecase\RecognitionProfiles.java:128` validates metadata against subject-directory capabilities, not application-specific entity names.
- `libs\java-shared\base-ai-backend\src\test\java\com\processpuzzle\ai\adapter\inbound\TenantDataCleanerTest.java:160` checks rollback suppression of external calls using a real transaction; `libs\java-shared\base-ai-backend\src\test\java\com\processpuzzle\ai\AiModularityTests.java:16` calls Modulith `verify()`.

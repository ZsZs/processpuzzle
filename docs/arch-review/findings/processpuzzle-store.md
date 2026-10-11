# Architecture review: processpuzzle-store

- **Slice code:** PST
- **Paths in scope:** `libs\java-shared\processpuzzle-store`
- **Reviewed at commit:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`
- **Date:** 2026-10-10
- **Tools run:** Static review using IDE file discovery, scoped glob/ripgrep, numbered PowerShell reads, file reads and Git revision verification. Read `AGENTS.md`, `README.md` architectural intent and the rubric. Inspected storage ports/adapters, upload/download, thumbnail and deletion orchestration, module declarations and relevant tests. Tests inspected, not executed; no build, Nx task, ArchUnit or Modulith verification executed.
- **Estimation note:** Effort is provisional: rubric calibration has not been completed. Scales and effort points are unchanged.

## Summary

The slice provides a useful storage port and separates internal object access from browser-facing URL signing. Its main debts concern the lifecycle of objects and the resource behavior hidden behind that port. PST-ABS-01 buffers uploads despite accepting streams, including a second full copy on the image path. PST-ABS-02 shows that ordinary deletion does not share the thumbnail cleanup policy used by release events. PST-OPS-01 records failed post-commit erasure only in logs, leaving no retryable failure outcome in the listener; its larger effort makes it a strategic debt despite its lower numerical priority.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PST-ABS-01 | The streaming storage port hides whole-object buffering | ABS | 3 | 3 platform | M | High | 4.5 |
| PST-ABS-02 | Ordinary deletion leaves the object's thumbnail behind | ABS | 3 | 2 feature | M | High | 3.0 |
| PST-OPS-01 | Failed post-commit erasure is acknowledged and forgotten | OPS | 4 | 2 feature | L | High | 2.0 |

## Findings

### PST-ABS-01 — The streaming storage port hides whole-object buffering

- **Category:** ABS
- **Criticality:** 3
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.5
- **Evidence:**
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\outbound\FileStorageService.java:15` — the public storage operation accepts an `InputStream`, without size or bounded-resource semantics.
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\outbound\MinioFileStorageService.java:79` — every upload calls `readAllBytes()` and only then submits a byte-array stream to Minio.
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\inbound\UploadObject.java:44` — image upload first buffers the full input itself and passes another byte-array stream to the storage adapter.
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\inbound\ThumbnailGenerator.java:13` — image decoding/resizing adds processing allocations while the original source bytes remain available.
  - `libs\java-shared\processpuzzle-store\src\test\java\com\processpuzzle\store\usecases\outbound\MinioFileStorageServiceTest.java:88` — the inspected upload test uses a small byte-array input; the image orchestration test at `libs\java-shared\processpuzzle-store\src\test\java\com\processpuzzle\store\usecases\inbound\UploadObjectTest.java:101` mocks the storage adapter.
- **Why it is debt:** The port makes streamed input look supported while the implementation makes heap consumption proportional to total object size. The normal image path retains the source array while the Minio adapter creates another full payload array, before considering decoded image memory or concurrent requests. Platform blast radius follows from a shared storage foundation running in the same backend process as other features: resource pressure is not isolated to the calling feature, although actual deployment limits and load were not assessed.
- **Suggested refactoring:** Define upload size and stream ownership explicitly at the port, and use bounded multipart streaming in the Minio adapter instead of whole-object buffering. Give thumbnail processing its own input-size, decoded-pixel and concurrency bounds, and avoid re-buffering already-owned bytes. Add an adapter-level non-byte-array stream test and a bounded-memory upload/thumbnail validation case.
- **Depends on / blocks:** none

### PST-ABS-02 — Ordinary deletion leaves the object's thumbnail behind

- **Category:** ABS
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\inbound\UploadObject.java:68` — successful image uploads create a separate object using `ThumbnailNaming.thumbnailKey(objectID)`.
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\adapters\inbound\ObjectEndpoint.java:42` — the ordinary DELETE endpoint delegates to `DeleteObject`.
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\inbound\DeleteObject.java:14` — that operation deletes only the supplied original key.
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\outbound\MinioFileStorageService.java:184` — the adapter's single delete removes exactly one object and adds no derivative cleanup.
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\inbound\GetThumbnailUri.java:16` — thumbnail lookup checks the derivative's existence, independently of the original.
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\adapters\inbound\ReleasedObjectsCleaner.java:70` — the release-event path, unlike ordinary deletion, expands each original key to include its thumbnail.
  - `libs\java-shared\processpuzzle-store\src\test\java\com\processpuzzle\store\usecases\inbound\DeleteObjectTest.java:24` — the deletion test verifies only the original-key storage call.
- **Why it is debt:** Ownership of the original-plus-derivative lifecycle is split between unrelated orchestration paths. Uploading an image and deleting it through the normal endpoint leaves the derivative stored, and the thumbnail URI operation can still return it. Callers therefore cannot treat successful deletion as removal of the complete stored image, while storage accumulates orphaned derivatives.
- **Suggested refactoring:** Introduce a shared object-family deletion operation that expands original keys to owned derivatives, and use it from both ordinary deletion and release-event cleanup. Keep raw single-key removal as an adapter primitive rather than the logical object lifecycle API. Add upload/delete/thumbnail-lookup tests plus idempotency and partial-failure cases.
- **Depends on / blocks:** none

### PST-OPS-01 — Failed post-commit erasure is acknowledged and forgotten

- **Category:** OPS
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.0
- **Evidence:**
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\adapters\inbound\ReleasedObjectsCleaner.java:47` — released files are deleted after the releasing transaction commits.
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\adapters\inbound\ReleasedObjectsCleaner.java:50` — a storage failure is caught and logged without rethrowing or recording retry work; lines 58–60 still emit the unconditional “Deleted” summary.
  - `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\outbound\MinioFileStorageService.java:235` — the adapter does expose lazy bulk-delete errors as `ObjectDeletionException`, but the listener discards that failure outcome.
  - `libs\java-shared\processpuzzle-store\src\test\java\com\processpuzzle\store\adapters\inbound\ReleasedObjectsCleanerTest.java:39` — the inspected failure test explicitly requires that a storage failure not escape the listener.
- **Why it is debt:** Waiting for commit correctly protects against premature file deletion, but it does not supply a reliable erasure lifecycle. On a Minio failure the listener returns normally, so even a publication registry observing listener completion cannot distinguish the failed cleanup from success; the application is left with logs and manual removal instructions instead of retryable work. This is a flaw in the documented best-effort choice, not an inference from the listener annotation: it undermines erasure for the feature that released its references and loses trustworthy completion reporting.
- **Suggested refactoring:** Record object-family cleanup as durable work associated with the release, and mark it complete only after the storage adapter succeeds. Retain failed keys, retry idempotently with backoff, and report pending/failed/succeeded counts rather than unconditional success. Add transaction-boundary and failure/restart tests; estimate includes durable state, operational visibility and recovery validation.
- **Depends on / blocks:** none

## Out-of-scope observations

- Application authorization, multipart limits, event-registry configuration, external lifecycle policies and release-event publishers were not investigated for this slice. Findings do not assume their absence.
- End-to-end tenant erasure also requires complete reference discovery by publishers; that independent concern is recorded as BEN-ABS-01 in the separately scoped base-entity report. Fixing delivery alone cannot recover references never included in a release.

## Strengths worth keeping

- Storage consumers can work through a dedicated port rather than a Minio client: `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\outbound\FileStorageService.java:9`.
- Internal and browser URL signing use explicitly distinct clients: `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\outbound\MinioFileStorageService.java:123` and line 154. The port documents the signed-host distinction at `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\outbound\FileStorageService.java:25`.
- Bulk deletion respects the S3 batch size, consumes lazy error results and rejects blank prefix erasure: `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\usecases\outbound\MinioFileStorageService.java:197`, line 210 and line 235.
- Module declarations and boundary verification tests exist: `libs\java-shared\processpuzzle-store\src\main\java\com\processpuzzle\store\package-info.java:9` and `libs\java-shared\processpuzzle-store\src\test\java\com\processpuzzle\store\StoreModularityTests.java:16`; these tests were not executed.

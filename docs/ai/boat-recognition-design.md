# Sailboat Recognition: Design Strategy

## 1. Goal and scope
Identify each full-size sailboat in a race-start video, using photos taken before the race (enrollment). Processing happens after the race start on a backend; the phone only records and uploads. Output: one confirmed boat identity per visible boat, with a manual review step for low-confidence cases.

**Out of scope (first version):** live on-device overlay, RC models, finish-line timing.

**Generic by design.** Sailboats are the first use case of a reusable recognition feature (`base-ai-frontend` /
`base-ai-backend`, Modulith module `ai`). The vocabulary used in the library:

| Generic term | Sailing instance |
|---|---|
| *Subject*: any base-entity object (`entityName` + `objectId`) | a boat |
| *Identifier text*: OCR-readable label, optional | the sail number |
| *Recognition profile*: per entity type, holds the detector class, OCR on/off, identifier pattern, fusion weights, thresholds | "Boat" profile: class `boat`, pattern like `[A-Z]{3} ?\d{1,5}` |
| *Recognition session*: one batch of media to identify against | a race start |
| *Candidate set*: the subjects allowed in a session | the start list |

Boats and races are not part of `ai`; they are base-entity definitions seeded by the sailing application.

## 2. Approach in one paragraph
Enrollment plus matching, not classification. Each boat is registered once with photos; no model is retrained when a boat is added. At recognition time the system detects and tracks boats, selects the best frames per boat, reads the sail number with OCR, computes an appearance embedding, fuses both scores and assigns identities one-to-one against the race start list.

## 3. Pipeline

### 3.1 Enrollment
1. Photograph each boat with sails up: both sides, bow, stern, 10-20 photos, varied light.
2. Detect the boat, crop it, compute a DINOv2 embedding per crop.
3. Read the sail number with OCR and store it as text.
4. Store crops, embeddings and the number per boat. Re-enroll when sails change.

### 3.2 Recognition (per uploaded video)
1. **Detect and track**: Ultralytics YOLO (or RT-DETR) plus ByteTrack; one track ID per boat.
2. **Best-frame selection** per track: score by crop size, sharpness (Laplacian variance) and how visible the sail is; keep the top 5-10 crops.
3. **Sail number OCR**: EasyOCR on the selected crops (at most 1280 px), and on their mirror image when that finds
   nothing; keep readings matching the profile's identifier pattern; vote across frames.
4. **Embedding**: DINOv2 on the same crops; average per track.
5. **Fusion**: combine OCR and embedding scores per (track, boat) pair.
6. **Assignment**: Hungarian algorithm (`scipy.optimize.linear_sum_assignment`) over the start list so each boat is claimed once.
7. **Review**: tracks below the confidence threshold go to a manual confirmation screen with the best crop and the top 3 candidates.

### 3.3 Scoring and fusion (starting point, tune on real data)
- `ocr_score`: fuzzy string similarity (e.g. `rapidfuzz`) between the voted number and the boat's registered number, 0..1.
- `emb_score`: cosine similarity between the track embedding and the boat's gallery (max or mean of top-k).
- `score = 0.6 * ocr_score + 0.4 * emb_score` (weights are a guess; calibrate on labeled clips).
- Assignment cost is `1 - score`. Accept a match if `score >= 0.75` and the margin to the second-best candidate is at least 0.1; otherwise send it to review.
- Restrict candidates to the boats registered for that race (and class, if known).

## 4. Components and libraries
| Concern | Choice | License note |
|---|---|---|
| Detection | **RT-DETR** (Hugging Face `transformers`) | Apache-2.0 |
| Tracking | ByteTrack from `supervision` | MIT |
| OCR | **EasyOCR** | Apache-2.0; chosen over PaddleOCR because it runs on the torch already loaded, where Paddle would add a second framework (~1 GB RAM) |
| Embeddings | DINOv2 (alt. OpenCLIP) | Apache-2.0 |
| Vector search | plain cosine, or FAISS | fleets are small; FAISS is optional |
| Assignment | SciPy | BSD |
| Service | FastAPI plus a worker queue (Celery or RQ), GPU worker | permissive |

**Decided: no paid or copyleft licenses.** Ultralytics is not used at all, including its bundled trackers, as the whole
package is AGPL-3.0. Every component above is Apache-2.0, MIT or BSD.

## 5. Architecture
- The main application (Spring Boot backend) owns races, boats and start lists, as base-entity objects.
- `base-ai-backend` owns recognition profiles, galleries (embeddings, identifier text), sessions, jobs, tracks and
  review decisions, in the stack's own database. It reaches the vision service only through an outbound port.
- The Python **vision server** (`apps/vision-server`, FastAPI) owns the models and the compute. It is
  **stateless with respect to tenant data**: it is called with media URLs and, for matching, the candidate
  gallery, and returns embeddings, tracks, OCR readings and scores. Its job queue is transient.
- Because it holds no tenant data, the vision server is **shared infrastructure**: one instance in
  `docker-compose-infrastructure.yaml`, used by every stack's backend, like Keycloak and MinIO.
- Media (photos, videos, crops) goes to MinIO; the phone uploads with presigned URLs, never through the backend.
- Video processing is asynchronous: upload, queue, worker, result via webhook callback to `base-ai-backend`, which
  publishes a domain event (`RecognitionCompleted`) for workflows to react to.

### 5.1 Compute: CPU only
There is no GPU on the stage and prod hosts, and no cloud service is used for now. All models run on CPU. That is
acceptable because processing is post-race and asynchronous; it is not acceptable for live use, which stays out of
scope.
- **Throughput.** Sample the video at 2-5 fps rather than processing every frame, detect at reduced resolution, and
  run OCR and embeddings only on the selected best crops at full resolution. Expect minutes per start video, not
  seconds.
- **Memory.** The models together need a few GB of RAM. The stage host has run out of memory before (see the
  runbook §7.6), so the vision server gets a hard memory limit, one worker, and loads models lazily.
- **Later.** If a customer pays for it, the same image runs on a GPU host or a cloud GPU worker; only the device
  setting and the deployment change.

## 6. API
Two contracts in `libs/java-shared/api-contracts/src/main/resources`, both OpenAPI 3.0.3:

| Contract | Between | Generated as |
|---|---|---|
| `ai-api.yaml` | frontend and `base-ai-backend` | Spring server interfaces, `com.processpuzzle.ai.api` / `.model` |
| `vision-server-api.yaml` | `base-ai-backend` and the vision server | `@HttpExchange` client interfaces, `com.processpuzzle.ai.vision.api` / `.model`; Pydantic models on the Python side |

### 6.1 `ai-api.yaml` (under `/organizations/{orgKey}`)
- **Profiles** `/recognition-profiles/{entityName}`: CRUD, one per entity type.
- **Media** `POST /media-uploads`: returns a presigned PUT URL and a `mediaKey`. The file goes straight to MinIO.
- **Enrollment** `/entities/{entityName}/{objectId}/enrollment`: `GET`, `DELETE`; `POST .../photos` with
  `mediaKeys` (202, idempotent per key); `DELETE .../photos/{photoId}`.
- **Sessions** `/recognition-sessions`: CRUD; a session names the entity type, an optional context object (the
  race) and the candidate object ids (the start list).
- **Jobs** `POST /recognition-sessions/{sessionId}/jobs` with a `mediaKey` (202); `GET /recognition-jobs/{jobId}`;
  `POST .../cancel`.
- **Tracks** `GET /recognition-jobs/{jobId}/tracks?status=NEEDS_REVIEW` is the review queue;
  `POST .../tracks/{trackId}/confirm` with an `objectId`, `POST .../reject`.
- **Callback** `POST /vision-notifications`: called by the vision server only, authenticated by a per-job token.

Track status: `AUTO_MATCHED`, `NEEDS_REVIEW`, `CONFIRMED`, `REJECTED`. The registered identifier is read from the
entity attribute named by the profile's `identifierAttributeKey`; the OCR reading of each enrollment photo is kept
beside it to flag mismatches.

### 6.2 `vision-server-api.yaml` (`/v1`, infrastructure network only)
- `POST /enrollment-jobs`, `/embedding-jobs`, `/recognition-jobs`: submit with a caller-chosen `jobId`
  (idempotent), presigned media URLs, a `callbackUrl` and `callbackToken` (sent back in an `X-Callback-Token`
  header, not as a bearer token, which the resource server would try to parse as a JWT). Recognition jobs carry the candidates'
  identifiers and gallery embeddings.
- `GET /{kind}-jobs/{jobId}/result`, `GET /jobs/{jobId}`, `DELETE /jobs/{jobId}`.
- `GET /models` (detector classes, embedding model and dimension, device), `GET /health`.

Flow: submit, 202, process, notify (no payload), `base-ai-backend` fetches the result, stores it, deletes the job.
`base-ai-backend` also polls jobs whose notification is overdue. Crops come back inline as base64 JPEG, embeddings as
base64 float32, each tagged with its model; a gallery of an older embedding model is re-embedded from its stored
crops (`/embedding-jobs`) before use. Errors are RFC 7807 problem details.

### 6.3 Events
Published by `base-ai-backend` post-commit, in `com.processpuzzle.shared.event`:
- `RecognitionCompletedEvent`: a job reached `DONE`.
- `SubjectIdentifiedEvent`: a track was auto-matched or confirmed; carries session, context, subject and score.

## 7. Data model (essentials)
In `base-ai-backend`, per stack database, every table scoped by `org_key`:
- `recognition_profile(entity_name, detector_class, identifier_attribute_key, identifier_pattern, matching settings)`
- `enrollment_photo(photo_id, entity_name, object_id, media_key, crop_key, status, observed_identifier, added_at)`
- `gallery_embedding(photo_id, model, vector)`
- `recognition_session(session_id, entity_name, context_entity_name, context_object_id, candidate_object_ids)`
- `recognition_job(job_id, session_id, media_key, status, progress, callback_token_hash, created_at, finished_at)`
- `track(job_id, track_id, status, object_id?, score, observed_identifier, best_crop_key, first_seen_ms, last_seen_ms, decided_by, decided_at)`
- `track_candidate(job_id, track_id, object_id, score, identifier_score, embedding_score)`

The vision server keeps only transient jobs and their results until fetched.

### 7.1 Outbound ports of `base-ai-backend`
`base-ai-backend` has no compile dependency on another feature; adapters live in the composition root.
- `VisionServer`: the vision server client.
- `MediaStore`: presigned upload and download URLs, crop storage, through `processpuzzle-store`.
- `SubjectDirectory`: whether an object exists and the value of its identifier attribute, through base-entity.

## 8. Quality and evaluation
- Build a small labeled test set from real start videos (track to boat ID).
- Metrics: identification accuracy per track, share sent to review, wrong auto-matches (the costly error).
- Keep confirmed crops as training data; fine-tune DINOv2 with metric learning (`pytorch-metric-learning`) if the baseline is not accurate enough.

## 9. Risks and mitigations
| Risk | Mitigation |
|---|---|
| Sail number hidden or mirrored when boats heel | Vote across frames; enroll both sides; fuzzy matching; embedding as a second signal |
| Overlapping boats at the start break tracks | Re-check identity by embedding after a break; review queue |
| Similar boats in one class (identical sails) | Rely on the number; require a margin over the second-best candidate |
| Small boats in frame | 4K recording, tripod or committee-boat mount, optical zoom, crop at full resolution |
| Changed sails after enrollment | Re-enrollment flow; keep photo dates |
| License constraints (AGPL detector) | Decide early; RT-DETR as the permissive option |

## 10. Phases
1. **Prototype**: detection, tracking, OCR on a few real clips; measure the OCR-only baseline.
2. **Enrollment and matching**: embeddings, fusion, assignment, the review screen.
3. **Service**: FastAPI, job queue, integration with the main backend.
4. **Tuning**: labeled test set, threshold and weight calibration, optional fine-tuning.
5. **Optional**: on-device or live processing if there is demand.

## 11. Decisions and open questions
Decided (2026-10-03):
- Detector: RT-DETR; no paid or copyleft components.
- Vision server: `apps/vision-server`, shared infrastructure, stateless with respect to tenant data.
- Compute: CPU only on the stage and prod hosts; no cloud service until a customer pays for it.
- Vocabulary: subject, identifier text, recognition profile, recognition session, candidate set.
- Stage runs the vision server within the host's current RAM, with small model variants (RT-DETR-R18,
  DINOv2-small) and a hard memory limit.

Open:
- Which classes or fleet sizes need to be supported?
- Is the start list always known in advance, and is class information available?
- Prod sizing of the vision server (RAM, a dedicated host, a GPU): decided later, from traffic and customer count.

## 12. Implementation status (2026-10-03)
| Part | State |
|---|---|
| `vision-server-api.yaml`, `ai-api.yaml` | written; Java types generated in `api-contracts` |
| `apps/vision-server` | service, job queue, enrollment / embedding / recognition pipelines, `vision-baseline` CLI; 56 unit and contract tests; runtime image with models baked in |
| `base-ai-backend` | profiles, media uploads, enrollment, vision job submission / callback / polling fallback; sessions, jobs and tracks answer 501 |
| Composition root | `MediaStore` over processpuzzle-store (bucket `<prefix>-ai-media`), `SubjectDirectory` over base-entity, callback path open in the security chain |
| `base-ai-frontend` | scaffold |
| Compose | `vision-server` in `docker-compose-infrastructure.yaml` (always on for stage/prod, `mem_limit` 2304m, loopback port 8190); behind the `ai` profile in the local/CI overlay, so `COMPOSE_PROFILES=ai` opts in |
| CI | `build-vision-server.yml` runs the tests and lint; Build/Deploy-Infrastructure build, push and promote the image with the other infrastructure images, cached in the registry |

Measured on a development machine (8 cores, Docker), CPU only, on two Wikimedia Commons photos:
- Enrollment: ~12 s per photo warm, ~15 s cold, OCR dominating. The 49er photo read `CAN603` at 0.91 with the
  identifier pattern; without the pattern, the neighbouring boat's `USA` won. The pattern is not optional in practice.
- Peak memory ~1.8 GB with all three models loaded. The stage host has about 3.8 GB for every container, so the
  vision server does not fit there beside the current stacks without either more RAM or a host of its own;
  `VISION_IDLE_UNLOAD_SECONDS` only returns the memory between jobs.

End to end on the local compose stack (2026-10-03): a profile, a presigned PUT straight to MinIO, enrollment, the
vision server's callback, the result fetch and the crop store all worked; the 49er photo came back ENROLLED with
`CAN603` in 28.8 s from a cold start. The vision server used ~320 MiB idle and ~630 MiB after the job.

### 12.1 Before the first stage deployment
- Enter `VISION_SERVICE_TOKEN` (one random value) in **both** Coolify resources and both GitHub Environments, and
  `PP_VISION_SERVER_PUBLISH=127.0.0.1:8190:8000` in the infrastructure resource.
- Make the GHCR package `processpuzzle-vision-server` pullable by the stage host — a package first pushed by a
  workflow is private.
- Room on the host for the 2304m limit.


# Sailboat Recognition: Design Strategy

## 1. Goal and scope
Tell which of a given list of objects is the one in front of the camera, using photos of those objects taken
beforehand. That is the whole job of the AI feature. The list is the only constraint, and the caller supplies it;
what the answer is used for is the caller's business. The sail race is the first example: before the start, at the
start and at the finish of every round, the race office points a phone at a boat, the AI answers which of the
enrolled boats it is, and the race application records that as an observation of its own.

**What the AI does not know.** Races, rounds, checkpoints, entry lists, observation records, time stamps and
locations are application data — ordinary base-entity definitions of the sailing application — and never appear
in `ai-api.yaml`. A race application builds its checkpoint screen from its own entities and hosts base-ai's camera
widget in it, handing in the candidates (the race's entry list) and recording the hits it reports.

**First version:** snapshot recognition — one to five frames from the camera, answered in seconds on CPU. Batch
recognition of a whole start video is the next step and reuses the same pipeline (§3.2); live on-device overlay and
RC models are out of scope.

**Generic by design.** Sailboats are the first use case of a reusable recognition feature (`base-ai-frontend` /
`base-ai-backend`, Modulith module `ai`). The vocabulary used in the library:

| Generic term | Sailing instance |
|---|---|
| *Subject*: any base-entity object (`entityName` + `objectId`) | a boat |
| *Identifier text*: OCR-readable label, optional | the sail number |
| *Recognition profile*: per entity type, holds the detector class, OCR on/off, identifier pattern, fusion weights, thresholds | "Boat" profile: class `boat`, pattern like `[A-Z]{3} ?\d{1,5}` |
| *Gallery*: the subject's reference photos, an ARTIFACT attribute of its own entity, named by the profile | the boat's `photos` |
| *Recognition*: one shot — a few frames — identified against a candidate list | a boat crossing the finish line |
| *Candidate list*: the subjects that may be in front of the camera, supplied by the caller | the race's entry list |

Boats, races and entry lists are not part of `ai`; they are base-entity definitions of the sailing application.

## 2. Approach in one paragraph
Enrollment plus matching, not classification. Each boat is registered once with photos; no model is retrained when a boat is added. At recognition time the system detects and tracks boats, selects the best frames per boat, reads the sail number with OCR, computes an appearance embedding, fuses both scores and assigns identities one-to-one against the candidate list the caller supplied — the race entry list, in the example.

## 3. Pipeline

### 3.1 Enrollment
1. Photograph each boat with sails up: both sides, bow, stern, 10-20 photos, varied light. The photos are attached
   to the boat itself, in the ARTIFACT attribute the profile's `galleryAttributeKey` names, and edited on the boat's
   own form.
2. Whenever the boat is saved, `base-ai-backend` synchronizes its gallery: photos new since the last time are
   enrolled, gallery entries of removed photos are dropped. It also synchronizes the candidates' galleries before a
   recognition, and on request.
3. Enrolling a photo: detect the boat, crop it, compute a DINOv2 embedding per crop, read the sail number with OCR.
4. `base-ai-backend` stores the crops, embeddings and readings per photo; never the photos, which stay the boat's.
   Re-enroll when sails change by replacing the photos.

### 3.2 Recognition
**Frames (implemented).** One to five shots of one subject. The largest detection of the profile's class in each
frame is a crop of it; the crops form one track, and steps 3-7 below apply to it. No tracking is needed, and at
most one subject is reported.

**Video (next).** Steps 1-7, per uploaded video:
1. **Detect and track**: Ultralytics YOLO (or RT-DETR) plus ByteTrack; one track ID per boat.
2. **Best-frame selection** per track: score by crop size, sharpness (Laplacian variance) and how visible the sail is; keep the top 5-10 crops.
3. **Sail number OCR**: EasyOCR on the selected crops (at most 1280 px), and on their mirror image when that finds
   nothing; keep readings matching the profile's identifier pattern; vote across frames.
4. **Embedding**: DINOv2 on the same crops; average per track.
5. **Fusion**: combine OCR and embedding scores per (track, boat) pair.
6. **Assignment**: Hungarian algorithm (`scipy.optimize.linear_sum_assignment`) over the start list so each boat is claimed once.
7. **Review**: a track below the confidence threshold is answered NEEDS_REVIEW with the best crop and the top 3 candidates; the camera widget lets a person choose.

### 3.3 Scoring and fusion (starting point, tune on real data)
- `ocr_score`: fuzzy string similarity (e.g. `rapidfuzz`) between the voted number and the boat's registered number, 0..1.
- `emb_score`: cosine similarity between the track embedding and the boat's gallery (max or mean of top-k).
- `score = 0.6 * ocr_score + 0.4 * emb_score` (weights are a guess; calibrate on labeled clips).
- Assignment cost is `1 - score`. Accept a match if `score >= 0.75` and the margin to the second-best candidate is at least 0.1; otherwise send it to review.
- Candidates are exactly the list the caller supplied — the boats entered in that race, and that class if known.

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
- The main application (Spring Boot backend) owns races, boats, entry lists and observations, as base-entity
  objects. The boats' reference photos are their own ARTIFACT attribute.
- `base-ai-backend` owns recognition profiles, galleries (crops, embeddings, identifier text) derived from the
  subjects' photos, and transient recognitions, in the stack's own database. It reaches the vision service only
  through an outbound port, and hears of subject changes from the composition root, which relays base-entity's
  object events onto its `SubjectGalleries` interface.
- The Python **vision server** (`apps/vision-server`, FastAPI) owns the models and the compute. It is
  **stateless with respect to tenant data**: it is called with media URLs and, for matching, the candidate
  gallery, and returns embeddings, tracks, OCR readings and scores. Its job queue is transient.
- Because it holds no tenant data, the vision server is **shared infrastructure**: one instance in
  `docker-compose-infrastructure.yaml`, used by every stack's backend, like Keycloak and MinIO.
- Media goes to MinIO: reference photos through processpuzzle-store like every artifact, camera frames with
  presigned URLs straight from the phone, never through the backend.
- Processing is asynchronous: upload, queue, worker, result via webhook callback to `base-ai-backend`; the client
  polls the recognition. No domain event is published — recording the answer is the caller's business.

### 5.1 Compute: CPU only
There is no GPU on the stage and prod hosts, and no cloud service is used for now. All models run on CPU. A snapshot
of a few frames takes seconds — acceptable at a checkpoint, where the shot's time is taken on the phone, not when
the answer arrives. A whole video takes minutes, which is why it is a batch job; live overlay stays out of scope.
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
- **Profiles** `/recognition-profiles/{entityName}`: CRUD, one per entity type. `galleryAttributeKey` names the
  ARTIFACT attribute holding the reference photos.
- **Media** `POST /media-uploads` (purpose `RECOGNITION_FRAME`): returns a presigned PUT URL and a `mediaKey`. The
  frame goes straight to MinIO.
- **Enrollment** `/entities/{entityName}/{objectId}/enrollment`: `GET` reads the gallery, `POST` synchronizes it
  with the subject's photos (202, idempotent), `DELETE` discards it so that the next synchronization rebuilds it.
- **Recognitions** `POST /recognitions` with `entityName`, 1-5 `mediaKeys` and the `candidateObjectIds` (202);
  `GET /recognitions/{recognitionId}`, polled until `DONE` or `FAILED`. Outcome `MATCHED` (with `objectId`),
  `NEEDS_REVIEW` (a person chooses among the top three `candidates`) or `NO_SUBJECT`. Kept for a day.
- **Callback** `POST /vision-notifications`: called by the vision server only, authenticated by a per-job token.

The registered identifier is read from the entity attribute named by the profile's `identifierAttributeKey`; the
OCR reading of each enrollment photo is kept beside it to flag mismatches.

### 6.2 `vision-server-api.yaml` (`/v1`, infrastructure network only)
- `POST /enrollment-jobs`, `/embedding-jobs`, `/recognition-jobs`: submit with a caller-chosen `jobId`
  (idempotent), presigned media URLs, a `callbackUrl` and `callbackToken` (sent back in an `X-Callback-Token`
  header, not as a bearer token, which the resource server would try to parse as a JWT). Recognition jobs carry the candidates'
  identifiers and gallery embeddings, and either a `video` or 1-5 `frames`.
- `GET /{kind}-jobs/{jobId}/result`, `GET /jobs/{jobId}`, `DELETE /jobs/{jobId}`.
- `GET /models` (detector classes, embedding model and dimension, device), `GET /health`.

Flow: submit, 202, process, notify (no payload), `base-ai-backend` fetches the result, stores it, deletes the job.
`base-ai-backend` also polls jobs whose notification is overdue. Crops come back inline as base64 JPEG, embeddings as
base64 float32, each tagged with its model; a gallery of an older embedding model is re-embedded from its stored
crops (`/embedding-jobs`) before use. Errors are RFC 7807 problem details.

### 6.3 Events
None published. The answer of a recognition is the caller's; a race application that wants an event publishes its
own when it records an observation.

## 7. Data model (essentials)
In `base-ai-backend`, per stack database, every table scoped by `org_key`:
- `recognition_profile(entity_name, detector_class, identifier_attribute_key, identifier_pattern, matching settings)`
- `enrollment_photo(photo_id, entity_name, object_id, photo_ref, crop_key, status, observed_identifier, embedding, added_at)` — `photo_ref` names the subject's artifact
- `recognition(recognition_id, entity_name, status, frame media keys, candidate object ids, outcome, object_id?, score, observed_identifier, crop_key, ranking, vision_job_id, created_at, finished_at)`, purged after `processpuzzle.ai.recognition.retention`

The vision server keeps only transient jobs and their results until fetched.

### 7.1 Outbound ports of `base-ai-backend`
`base-ai-backend` has no compile dependency on another feature; adapters live in the composition root.
- `VisionServer`: the vision server client.
- `MediaStore`: presigned upload and download URLs, crop storage, through `processpuzzle-store`.
- `SubjectDirectory`: whether an object exists, the value of its identifier attribute, and its photos with signed
  URLs, through base-entity and processpuzzle-store.

Inbound, `ai :: galleries` exposes `SubjectGalleries` (`subjectChanged`, `subjectDeleted`), which the composition
root calls from base-entity's object events.

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
- Vocabulary: subject, identifier text, recognition profile, recognition session, candidate set (sessions superseded by recognitions on 2026-10-04).
- Stage runs the vision server within the host's current RAM, with small model variants (RT-DETR-R18,
  DINOv2-small) and a hard memory limit.

Decided (2026-10-04):
- The AI knows nothing of its usage: frames and a candidate list in, ranked candidates out. Races, checkpoints and
  observations are application data.
- Reference photos are the subject's own ARTIFACT attribute; base-ai's own photo upload is gone.
- Snapshot recognition (1-5 frames) first; video batch next, on the same pipeline.

Open:
- Which classes or fleet sizes need to be supported?
- Is class information available to narrow the candidate list?
- Prod sizing of the vision server (RAM, a dedicated host, a GPU): decided later, from traffic and customer count.

## 12. Implementation status (2026-10-04)
| Part | State |
|---|---|
| `vision-server-api.yaml`, `ai-api.yaml` | written; Java types generated in `api-contracts`; recognition takes frames or a video |
| `apps/vision-server` | service, job queue, enrollment / embedding / recognition pipelines (video and frames), `vision-baseline` CLI; unit and contract tests; runtime image with models baked in |
| `base-ai-backend` | profiles, frame uploads, galleries synchronized from the subjects' ARTIFACT photos, recognitions against a candidate list, vision job submission / callback / polling fallback; video recognition not offered yet |
| Composition root | `MediaStore` over processpuzzle-store (bucket `<prefix>-ai-media`), `SubjectDirectory` over base-entity and the store, base-entity object events relayed to `SubjectGalleries`, callback path open in the security chain |
| `base-ai-frontend` | Recognition Profile screens; read-only Recognition tab (the enrollment) contributed onto every profiled entity (gallery, readings, mismatch flags, synchronize, rebuild); `pp-recognition-camera` widget |
| Testbed | `base-ai` section: Overview and Samples — the seeded `boat` profile, the `Boat` entity with its `photos` attribute and four boats (CAN 603 matching the Wikimedia sample photo), and the race application around it as plain base-entity metadata — `Race` (rounds), `Registration` (race ↔ boat, the candidate constraint) and `Race Observation` (race, round, checkpoint, boat, capturedAt, source) — with a Recognize page that picks the context, hands the race's entries to the camera widget and saves every hit, or hand tick, as an observation |
| Compose | `vision-server` in `docker-compose-infrastructure.yaml` (always on for stage/prod, `mem_limit` 2304m, loopback port 8190); behind the `ai` profile in the local/CI overlay, so `COMPOSE_PROFILES=ai` opts in |
| CI | `build-vision-server.yml` runs the tests and lint; Build/Deploy-Infrastructure build, push and promote the image with the other infrastructure images, cached in the registry |

Databases from before 2026-10-04 have to be dropped and reseeded: the schema changed, and seeded definitions are
create-only, so an existing `boat` definition would lack the `photos` attribute and the `boat` profile its
`galleryAttributeKey`. There is no migration before Flyway is introduced.

Measured on a development machine (8 cores, Docker), CPU only, on two Wikimedia Commons photos:
- Enrollment: ~12 s per photo warm, ~15 s cold, OCR dominating. The 49er photo read `CAN603` at 0.91 with the
  identifier pattern; without the pattern, the neighbouring boat's `USA` won. The pattern is not optional in practice.
- Peak memory ~1.8 GB with all three models loaded. The stage host has about 3.8 GB for every container, so the
  vision server does not fit there beside the current stacks without either more RAM or a host of its own;
  `VISION_IDLE_UNLOAD_SECONDS` only returns the memory between jobs.

### 12.1 Before the first stage deployment
- Enter `VISION_SERVICE_TOKEN` (one random value) in **both** Coolify resources and both GitHub Environments, and
  `PP_VISION_SERVER_PUBLISH=127.0.0.1:8190:8000` in the infrastructure resource.
- Make the GHCR package `processpuzzle-vision-server` pullable by the stage host — a package first pushed by a
  workflow is private.
- Room on the host for the 2304m limit.


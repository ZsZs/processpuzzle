# base-ai-backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-base-ai-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_base_ai_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_base_ai_backend)

## Introduction

`base-ai-backend` is the Spring Boot half of ProcessPuzzle's **image recognition** — identifying known objects,
such as racing sailboats by their sail numbers, in photos and videos. It is a Spring Modulith application
module (`ai`) that owns everything tenant-specific about recognition: the recognition profiles, every
subject's gallery of photos, crops and embeddings, and the jobs that process them.

What it does *not* do is run the models. Detection, OCR and embeddings run in the **vision server**, a separate
Python service shared by every application stack. This module sends it work and stores what comes back, so the
vision server can stay stateless and be scaled, moved to a GPU host or replaced without touching tenant data.

## Architecture

```mermaid
sequenceDiagram
  autonumber
  participant UI as Browser
  participant BE as base-ai-backend
  participant S3 as Object storage
  participant VS as Vision server

  UI->>BE: POST /media-uploads
  BE-->>UI: mediaKey + presigned PUT URL
  UI->>S3: PUT photo
  UI->>BE: POST /entities/boat/{id}/enrollment/photos {mediaKeys}
  BE->>BE: photos PENDING, job ticket saved
  BE-->>UI: 202 enrollment (PROCESSING)
  BE->>VS: submit enrollment job (internal photo URLs, callback token)
  VS->>S3: GET photos
  VS->>VS: detect · crop · embed · read identifier
  VS->>BE: POST /vision-notifications (job DONE)
  BE->>VS: GET result
  BE->>S3: store crops
  BE->>BE: photos ENROLLED with embedding and reading
  UI->>BE: GET enrollment (READY)
```

Three properties of this flow are deliberate:

- **Media bypasses the backend.** Photos and videos go from the client to object storage and from there to the
  vision server, through presigned URLs. A race-start video runs to gigabytes; streaming it through Spring would
  buy nothing.
- **Nothing is lost when the vision server is down.** The photos and their job ticket are committed before the
  job is submitted. If submission fails the ticket stays open, and a poller resubmits it — and chases any job
  whose notification never arrived.
- **A notification carries no data.** It only says a job finished; the backend then fetches the result itself.
  Each job has its own random callback token, of which only a hash is stored, so a forged or replayed
  notification can at most trigger a fetch of a real result.

### Module boundaries

The module compiles against no other feature. What it needs from the rest of the platform it declares as
outbound ports in `ai :: port`, which the deploying application implements:

| Port | Question it answers | Testbed adapter |
|---|---|---|
| `MediaStore` | presigned upload / download URLs, storing crops | `processpuzzle-store` (MinIO), bucket `<prefix>-ai-media` |
| `SubjectDirectory` | does the entity type exist, is an attribute TEXT, does the object exist, what is its identifier | base-entity's `EntityObjectAccess` |
| `VisionServer` | submit jobs, read results | this module's own HTTP adapter, generated from `vision-server-api.yaml` |

`SubjectDirectory` defaults to permitting, so without an adapter any entity type is accepted and matching
falls back to appearance alone. `MediaStore` has no sensible default and answers 503 when it is missing.

## API

`ai-api.yaml` in `api-contracts`, under `/organizations/{orgKey}`:

| Resource | Operations | State |
|---|---|---|
| `/recognition-profiles`, `/recognition-profiles/{entityName}` | list, create, get, replace, delete | implemented |
| `/media-uploads` | reserve an upload slot (presigned PUT URL) | implemented |
| `/entities/{entityName}/{objectId}/enrollment` | get, delete the whole gallery | implemented |
| `…/enrollment/photos`, `…/enrollment/photos/{photoId}` | add uploaded photos (202), remove one | implemented |
| `/vision-notifications` | the vision server's callback, `X-Callback-Token` header | implemented |
| `/recognition-sessions`, `/recognition-jobs/{jobId}`, `…/tracks` | sessions, video jobs, review (confirm / reject) | next — answer 501 today |
| `/ai/translations/{locale}` | the feature's UI bundles | implemented, empty |

Refusals use the platform's `ErrorResponse` with an `errorId` the frontend translates, for example
`ai.profile.entity-not-found`, `ai.media.not-uploaded` or `ai.media.content-type-unsupported`.

### Profile rules

A profile is one per entity type, keyed by the type's **definition code** (`boat`). On save the backend checks
that the type exists, that `identifierAttributeKey` names one of its TEXT attributes, that `identifierPattern`
compiles, and that every threshold is in range. The identifier pattern matters more than it looks: without one,
any confident text on the subject counts as its identifier — a sponsor's name, a neighbouring boat's number.

| Setting | Default | Meaning |
|---|---|---|
| `identifierWeight` | 0.6 | weight of the identifier score in the fused score; appearance gets the rest |
| `acceptScore` | 0.75 | minimum fused score for an automatic match |
| `acceptMargin` | 0.1 | minimum lead over the second-best candidate |
| `sampleFps` | 3 | frames per second of video looked at — the main CPU cost lever |

## Using it in an application

**1. Depend on it** — `com.processpuzzle:base-ai-backend` — and let the application's component scan reach
`com.processpuzzle`.

**2. Implement the two ports** in the composition root, never inside a feature library:

```java
@Bean MediaStore aiMediaStore(FileStorageService storage, @Value("${minio.bucket-prefix:}") String prefix) { … }
@Bean SubjectDirectory aiSubjectDirectory(EntityAttributeQuery attributes, EntityObjectAccess objects) { … }
```

**3. Let the callback through the security chain.** `POST /organizations/*/vision-notifications` carries no user
token — it is authenticated by its `X-Callback-Token` — so the resource server must not challenge it:

```java
requests.requestMatchers(HttpMethod.POST, "/organizations/*/vision-notifications").permitAll();
```

**4. Configure the vision server** (`processpuzzle.ai`):

| Property | Default | |
|---|---|---|
| `vision-server.base-url` | — | e.g. `http://processpuzzle-vision-server:8000/v1`; unset means photos stay PENDING |
| `vision-server.service-token` | — | the vision server's `VISION_SERVICE_TOKEN` |
| `vision-server.callback-base-url` | — | this backend as the vision server reaches it, e.g. `http://testbed-backend:8080` |
| `vision-server.stack` | `processpuzzle` | names this stack in the vision server's logs and fair queuing |
| `media.upload-expiry` | `PT30M` | how long a client has to finish an upload |
| `media.max-photo-bytes` / `max-video-bytes` | 40 MB / 4 GB | |
| `poller.interval` / `poller.overdue-after` | `PT1M` / `PT10M` | the fallback that resubmits and chases jobs |

**5. Seed profiles (optional).** With `base-ai.loadDefaultProfiles=true` the module imports every
`classpath*:default-recognition-profiles/<orgKey>-recognition-profiles.yaml` on startup. Each file lists
`recognitionProfiles` in the API's `RecognitionProfileInput` shape. The import is create-only: a profile
already present is never overwritten, so a tuned threshold survives restarts.

## Data

All tables carry `org_key`; the module holds no shared state across tenants.

| Table | Holds |
|---|---|
| `ai_recognition_profiles` | one profile per (organization, entity type) |
| `ai_media_uploads` | upload slots; unused ones are swept, with their objects, after they expire |
| `ai_enrollment_photos` | each gallery photo: status, crop location, embedding and its model, reading |
| `ai_vision_job_tickets` | this side's record of every vision job, and the hash of its callback token |

Embeddings are tagged with the model that produced them, because vectors of two models are not comparable: the
vision server refuses a recognition job whose gallery names another model than its own. The crops are kept for
exactly that case — the vision server's embedding job recomputes a gallery from them, so an upgraded model never
means photographing the fleet again. Triggering that re-embedding from this module is not wired up yet.

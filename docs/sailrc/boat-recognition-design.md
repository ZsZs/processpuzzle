# Sailboat Recognition: Design Strategy

## 1. Goal and scope
Identify each full-size sailboat in a race-start video, using photos taken before the race (enrollment). Processing happens after the race start on a backend; the phone only records and uploads. Output: one confirmed boat identity per visible boat, with a manual review step for low-confidence cases.

**Out of scope (first version):** live on-device overlay, RC models, finish-line timing.

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
3. **Sail number OCR**: PaddleOCR on the selected crops at full resolution; vote across frames.
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
| Detection | Ultralytics YOLO or RT-DETR | YOLO is AGPL-3.0; RT-DETR (Hugging Face) is Apache-2.0 |
| Tracking | ByteTrack / BoT-SORT | permissive |
| OCR | PaddleOCR (alt. EasyOCR) | Apache-2.0 |
| Embeddings | DINOv2 (alt. OpenCLIP) | Apache-2.0 |
| Vector search | plain cosine, or FAISS | fleets are small; FAISS is optional |
| Assignment | SciPy | BSD |
| Service | FastAPI plus a worker queue (Celery or RQ), GPU worker | permissive |

If the product will be distributed commercially, decide on the detector's license early.

## 5. Architecture
- The main application (Spring Boot backend) owns races, boats and start lists.
- A separate Python **vision service** owns models, embeddings and jobs and exposes a REST API.
- Media (photos, videos, crops) goes to object storage; the service keeps embeddings and job state.
- Video processing is asynchronous: upload, queue, GPU worker, result via polling or webhook.

## 6. API sketch (vision service, `/v1`)

### Enrollment
- `POST /boats/{boatId}/enrollment-photos`: multipart upload of one or more photos; body may include `sailNumber`. Returns `202` with the number of accepted photos.
- `GET /boats/{boatId}/enrollment`: status, photo count, stored sail number.
- `DELETE /boats/{boatId}/enrollment`: remove the gallery (for re-enrollment or data deletion).

### Recognition jobs
- `POST /races/{raceId}/recognition-jobs`: upload a video (multipart) or pass a media URL. Optional fields: `boatIds[]` (start list), `classId`, `callbackUrl`. Returns `202 { "jobId": "..." }`.
- `GET /recognition-jobs/{jobId}`: `{ "status": "queued|running|done|failed", "progress": 0.0-1.0 }`.
- `GET /recognition-jobs/{jobId}/results`: the tracks with candidates (see below).
- `POST /recognition-jobs/{jobId}/tracks/{trackId}/confirm`: body `{ "boatId": "..." }`; records a manual decision.
- `POST /recognition-jobs/{jobId}/tracks/{trackId}/reject`: marks the track as "not a registered boat".

### Result shape
```json
{
  "jobId": "j-123",
  "tracks": [
    {
      "trackId": 7,
      "status": "auto_matched",
      "boatId": "b-42",
      "score": 0.91,
      "ocr": { "text": "GER 1234", "confidence": 0.88 },
      "bestCropUrl": "/media/j-123/t-7/best.jpg",
      "candidates": [
        { "boatId": "b-42", "score": 0.91 },
        { "boatId": "b-17", "score": 0.62 }
      ]
    },
    {
      "trackId": 9,
      "status": "needs_review",
      "boatId": null,
      "candidates": [ { "boatId": "b-08", "score": 0.68 } ]
    }
  ]
}
```
Track `status` values: `auto_matched`, `needs_review`, `confirmed`, `rejected`.

### Conventions
- Authentication via bearer token issued by the main application; the service is not exposed publicly.
- Idempotent uploads (client-supplied `Idempotency-Key`); errors follow RFC 7807 problem details.

## 7. Data model (essentials)
- `boat_gallery(boat_id, sail_number, embedding, crop_url, created_at)`
- `recognition_job(job_id, race_id, status, video_url, created_at)`
- `track(job_id, track_id, boat_id?, score, status, ocr_text, best_crop_url)`
- `candidate(job_id, track_id, boat_id, score)`

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

## 11. Open questions
- Which classes or fleet sizes need to be supported?
- Is the start list always known in advance, and is class information available?
- Is a GPU available at the deployment site, or should processing run in the cloud?
- Commercial distribution plans (affects the detector license)?

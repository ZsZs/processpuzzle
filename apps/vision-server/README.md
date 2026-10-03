# vision-server

The compute half of ProcessPuzzle's object recognition: detection (RT-DETR), tracking (ByteTrack from
`supervision`), identifier OCR (EasyOCR) and appearance embeddings (DINOv2), plus the fusion and one-to-one
assignment of tracks to candidates. Python 3.12 / FastAPI, CPU only.

- Contract: [`vision-server-api.yaml`](../../libs/java-shared/api-contracts/src/main/resources/vision-server-api.yaml).
  Its only client is `base-ai-backend`; `tests/test_contract.py` keeps `schemas.py` aligned with it.
- Design: [boat-recognition-design.md](../../docs/ai/boat-recognition-design.md).
- Shared infrastructure, stateless with respect to tenant data: jobs and results are transient.

## Build and test
Everything runs in Docker, so no local Python is needed:

```shell
npm exec nx test vision-server          # unit + contract tests, no model library installed
npm exec nx lint vision-server          # ruff
npm exec nx docker-build vision-server  # runtime image, models baked in
```

## Run
```shell
docker run --rm -p 8000:8000 -e VISION_SERVICE_TOKEN=dev-token processpuzzle-vision-server:local
```

| Variable | Default | |
|---|---|---|
| `VISION_SERVICE_TOKEN` | — | required; bearer token of every call except `/v1/health` |
| `VISION_DETECTOR_MODEL` | `PekingU/rtdetr_r18vd_coco_o365` | Hugging Face id; Apache-2.0 |
| `VISION_EMBEDDING_MODEL` | `facebook/dinov2-small` | Hugging Face id; Apache-2.0 |
| `VISION_OCR_LANGUAGES` | `en` | EasyOCR language codes |
| `VISION_TORCH_THREADS` | `0` (torch default) | cap CPU use on a shared host |
| `VISION_MAX_QUEUED_JOBS` | `20` | beyond it, submissions answer 503 |
| `VISION_IDLE_UNLOAD_SECONDS` | `600` | models are dropped after this long idle; `0` keeps them |
| `VISION_RESULT_RETENTION_SECONDS` | `86400` | how long a finished job's result is kept |

## Phase 1 baseline on a real clip
`vision-baseline` runs recognition on a local video without the HTTP service and writes `tracks.json`, one
crop per track and `report.json`:

```shell
docker run --rm -v "$PWD/clips:/clips" --entrypoint vision-baseline processpuzzle-vision-server:local \
  /clips/start.mp4 --start-list /clips/start-list.csv --pattern '^[A-Z]{3} ?[0-9]{1,5}$' \
  --present "GER 1234" "NED 77" --out /clips/out
```

`start-list.csv` holds `candidateId,identifier` rows. Without galleries, matching is by sail number alone — the
OCR-only baseline.

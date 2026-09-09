# MinIO Docker Setup — ProcessPuzzle

Custom MinIO image with pre-configured buckets and a dedicated Spring Boot service account.

## Folder Structure

```
minio/
├── Dockerfile         # Custom MinIO image
├── init-minio.sh      # Bucket + policy initialization script
├── docker-compose.yml # Compose file
└── README.md
```

## Pre-configured Resources

| Resource | Value |
|---|---|
| Buckets | `<stack-prefix>-<purpose>` for each of the two stacks — 16 in all; see below |
| Bucket policy | Private (authenticated only) |
| Admin user | `minioadmin` / `minioadmin` |
| Service account | `springboot` / `springboot123` |

## Buckets are per application stack

One MinIO serves every application stack, and the **bucket prefix is the whole isolation between
them** — see [`docs/application-stacks.md`](../../../docs/application-stacks.md). A bucket is named
`<stack-prefix>-<purpose>`, e.g. `processpuzzle-admin-documents`.

- **prefixes**: `processpuzzle-testbed`, `processpuzzle-admin` — each backend's `MINIO_BUCKET_PREFIX`.
- **purposes**: `configuration`, `text`, `images`, `documents`, `audio`, `video`, `archives`, `logs` —
  `minio.buckets` in `libs/java-shared/processpuzzle-store/src/main/resources/minio-config.yaml`.

`BucketNameFinder` is the only place that applies the prefix, because it is the only place a bucket
name is *chosen*: `ObjectEndpoint` echoes back names the server handed out earlier, and `UploadObject`
asks `CreateBucket` for whatever it needs. The prefix defaults to empty, so a deployment that sets
nothing keeps the flat names.

Two things follow from that:

- **`init-minio.sh` is a convenience, not a precondition.** It used to create two of the eight
  purposes and the other six worked anyway, because `CreateBucket` makes a missing bucket on demand.
  Adding a purpose to `minio-config.yaml` without adding it here is not a bug.
- **Objects in the old unprefixed `documents` / `images` buckets are unreachable**, since nothing looks
  in them any more. Local development data only.

> **Half wired up in production.** MinIO is now part of the one shared infrastructure definition,
> `tools/docker/docker-compose-infrastructure.yaml`, so every environment runs it — the separate
> prod compose file that had no MinIO service at all is gone. What remains is that
> `minio-config.yaml` hard-codes `http://localhost:7000`, so the endpoint still needs making
> per-environment before production object storage works.

## Usage

```bash
# Build and start
docker compose up -d --build

# View initialization logs
docker logs processpuzzle-minio

# Stop
docker compose down

# Stop and delete all data
docker compose down -v
```

## Access

| Interface | URL |
|---|---|
| Web Console | http://localhost:9001 |
| S3 API | http://localhost:9000 |

## Spring Boot Configuration

```yaml
# application-dev.yml
minio:
  endpoint: http://localhost:9000
  access-key: springboot
  secret-key: springboot123
  bucket-prefix: processpuzzle-testbed   # or set MINIO_BUCKET_PREFIX
  buckets:
    documents: documents
    images: images
```

## Changing Credentials

Update the environment variables in `docker-compose.yml`:

```yaml
environment:
  MINIO_ROOT_USER: your-admin-user
  MINIO_ROOT_PASSWORD: your-admin-password
  MINIO_SERVICE_USER: your-service-user
  MINIO_SERVICE_PASSWORD: your-service-password
```

Never commit real credentials — use a `.env` file instead:

```bash
# .env (git-ignored)
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
MINIO_SERVICE_USER=springboot
MINIO_SERVICE_PASSWORD=springboot123
```

```yaml
# docker-compose.yml
environment:
  MINIO_ROOT_USER: ${MINIO_ROOT_USER}
  MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
  MINIO_SERVICE_USER: ${MINIO_SERVICE_USER}
  MINIO_SERVICE_PASSWORD: ${MINIO_SERVICE_PASSWORD}
```

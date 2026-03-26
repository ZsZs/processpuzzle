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
| Buckets | `documents`, `images` |
| Bucket policy | Private (authenticated only) |
| Admin user | `minioadmin` / `minioadmin` |
| Service account | `springboot` / `springboot123` |

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

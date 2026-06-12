# ── Stage 1: MinIO server base image ──────────────────────────────────────────
FROM quay.io/minio/minio:latest AS minio

# ── Stage 2: MinIO Client (mc) for bucket/policy initialization ───────────────
FROM quay.io/minio/mc:latest AS mc

# ── Final image ───────────────────────────────────────────────────────────────
FROM quay.io/minio/minio:latest

# Copy MinIO Client binary from mc image
COPY --from=mc /usr/bin/mc /usr/bin/mc

# Copy initialization script
COPY ./tools/docker/minio/init-minio.sh /usr/local/bin/init-minio.sh
RUN chmod +x /usr/local/bin/init-minio.sh

# Expose S3 API and Web Console ports
EXPOSE 9000 9001

# Use entrypoint script that starts MinIO AND initializes buckets
ENTRYPOINT ["/usr/local/bin/init-minio.sh"]

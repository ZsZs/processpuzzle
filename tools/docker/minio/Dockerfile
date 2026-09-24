# MinIO withdrew its public images from quay.io and Docker Hub on 2026-09-24. pgsty/* are community
# rebuilds of the upstream releases; pin RELEASE tags rather than `latest`, as the fork is third-party.
ARG MINIO_IMAGE=pgsty/minio:RELEASE.2026-08-04T00-00-00Z
ARG MC_IMAGE=pgsty/mc:RELEASE.2026-09-16T00-00-00Z

# ── MinIO Client (mc) for bucket/policy initialization ────────────────────────
FROM ${MC_IMAGE} AS mc

# ── Final image ───────────────────────────────────────────────────────────────
FROM ${MINIO_IMAGE}

# Copy MinIO Client binary from mc image
COPY --from=mc /usr/bin/mc /usr/bin/mc

# Copy initialization script
COPY ./tools/docker/minio/init-minio.sh /usr/local/bin/init-minio.sh
RUN chmod +x /usr/local/bin/init-minio.sh

# Expose S3 API and Web Console ports
EXPOSE 9000 9001

# Use entrypoint script that starts MinIO AND initializes buckets
ENTRYPOINT ["/usr/local/bin/init-minio.sh"]

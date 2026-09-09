#!/bin/sh
set -e

# ── Configuration ──────────────────────────────────────────────────────────────
MINIO_ALIAS="local"
MINIO_ENDPOINT="http://localhost:9000"

# One MinIO serves every application stack, isolated by bucket prefix: bucket names are
# <stack-prefix>-<purpose>, and the prefix is each backend MINIO_BUCKET_PREFIX. See
# docs/application-stacks.md.
#
# PURPOSES must match minio.buckets in libs/java-shared/processpuzzle-store/src/main/resources/
# minio-config.yaml. This script used to create two of the eight; the other six worked anyway,
# because UploadObject asks CreateBucket for whatever bucket it needs and MinIO makes it on the
# spot. So the list here is a convenience — a console with the expected buckets already in it — and
# not a precondition. Adding a purpose to minio-config.yaml without adding it here is not a bug.
# Both stacks, though only the testbed's application is built in this repository: the prefixes are
# shared infrastructure, and the private admin backend writes under the second one. See
# docs/platform-admin-extraction.md.
STACK_PREFIXES="processpuzzle-testbed processpuzzle-admin"
PURPOSES="configuration text images documents audio video archives logs"

BUCKETS=""
for PREFIX in ${STACK_PREFIXES}; do
  for PURPOSE in ${PURPOSES}; do
    BUCKETS="${BUCKETS} ${PREFIX}-${PURPOSE}"
  done
done

# ── Start MinIO server in background ──────────────────────────────────────────
echo "Starting MinIO server..."
minio server /data --console-address ":9001" &
MINIO_PID=$!

# ── Wait for MinIO to be ready ────────────────────────────────────────────────
echo "Waiting for MinIO to be ready..."
until mc alias set "${MINIO_ALIAS}" "${MINIO_ENDPOINT}" \
  "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}" > /dev/null 2>&1; do
  echo "  MinIO not ready yet, retrying in 1s..."
  sleep 1
done
echo "MinIO is ready."

# ── Create buckets with private policy ────────────────────────────────────────
for BUCKET in ${BUCKETS}; do
  if mc ls "${MINIO_ALIAS}/${BUCKET}" > /dev/null 2>&1; then
    echo "Bucket '${BUCKET}' already exists, skipping."
  else
    echo "Creating bucket '${BUCKET}'..."
    mc mb "${MINIO_ALIAS}/${BUCKET}"

    # Private policy — no anonymous access
    mc anonymous set none "${MINIO_ALIAS}/${BUCKET}"

    echo "Bucket '${BUCKET}' created with private policy."
  fi
done

# ── Create a dedicated service account for Spring Boot ────────────────────────
echo "Creating Spring Boot service account..."
mc admin user add "${MINIO_ALIAS}" \
  "${MINIO_SERVICE_USER:-springboot}" \
  "${MINIO_SERVICE_PASSWORD:-springboot123}" 2>/dev/null || \
  echo "Service account already exists, skipping."

# Attach readwrite policy to service account
mc admin policy attach "${MINIO_ALIAS}" readwrite \
  --user "${MINIO_SERVICE_USER:-springboot}" 2>/dev/null || \
  echo "Policy already attached, skipping."

echo "✅ MinIO initialization complete."
echo "   Buckets: ${BUCKETS}"
echo "   Service account: ${MINIO_SERVICE_USER:-springboot}"

# ── Hand off to MinIO process ─────────────────────────────────────────────────
wait ${MINIO_PID}

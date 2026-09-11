# Use the Alpine version for a smaller image footprint (~100MB vs ~400MB)
FROM postgres:18.1-alpine

# Set the system timezone (crucial for accurate token timestamps)
ENV TZ=UTC

# Metadata labels
LABEL maintainer="Admin"
LABEL version="1.0"
LABEL description="PostgreSQL image optimized for Keycloak"

# Copy the application database bootstrap. PostgreSQL runs it automatically on first initialization;
# the `postgres-init` one-shot service invokes the same idempotent script after every later startup,
# so newly added databases reach existing volumes too.
COPY ./tools/docker/postgresql/10-init-db.sh /docker-entrypoint-initdb.d/
RUN chmod +x /docker-entrypoint-initdb.d/10-init-db.sh

EXPOSE 5432
# (Optional) Optimization: Adjust memory settings for the DB
# These can also be passed via the command line
CMD ["postgres", "-c", "max_connections=200", "-c", "shared_buffers=256MB"]

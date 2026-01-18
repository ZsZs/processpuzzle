# Use the Alpine version for a smaller image footprint (~100MB vs ~400MB)
FROM postgres:18.1-alpine

# Set the system timezone (crucial for accurate token timestamps)
ENV TZ=UTC

# Metadata labels
LABEL maintainer="Admin"
LABEL version="1.0"
LABEL description="PostgreSQL image optimized for Keycloak"

# Copy initialization scripts
# Any .sql or .sh scripts in this folder run automatically on first container start
COPY ./docker/postgresql/init-db.sql /docker-entrypoint-initdb.d/

EXPOSE 5432
# (Optional) Optimization: Adjust memory settings for the DB
# These can also be passed via the command line
CMD ["postgres", "-c", "max_connections=200", "-c", "shared_buffers=256MB"]

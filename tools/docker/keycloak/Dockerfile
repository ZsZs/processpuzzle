# Stage 1: Build/Optimization stage
FROM quay.io/keycloak/keycloak:26.4.7 AS builder

# Enable health and metrics support
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

# Configure the database vendor (e.g., postgres, mysql, oracle, mssql)
ENV KC_DB=postgres

# (Optional) Add custom providers or themes
# COPY ./providers/*.jar /opt/keycloak/providers/
# COPY ./themes/custom-theme /opt/keycloak/themes/custom-theme

WORKDIR /opt/keycloak
# Run the build command to create an optimized image
RUN /opt/keycloak/bin/kc.sh build

# Stage 2: Runtime stage
FROM quay.io/keycloak/keycloak:26.4.7
COPY --from=builder /opt/keycloak/ /opt/keycloak/

# Bake the realm definition into the image. --import-realm on start is idempotent:
# it imports realms only if they do not already exist in the database.
COPY tools/docker/keycloak/import/ /opt/keycloak/data/import/

# Set execution directory
WORKDIR /opt/keycloak

# Environment variables for runtime (can be overridden at runtime)
ENV KC_DB=postgres
ENV KC_HTTP_ENABLED=true
ENV KC_PROXY=edge
ENV KC_HOSTNAME_STRICT=false

# Expose ports: 8080 (HTTP), 8443 (HTTPS), 9000 (Management/Health)
EXPOSE 8080 8443 9000

# Start Keycloak with the 'optimized' flag and import any realm JSON in /opt/keycloak/data/import/
ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized", "--import-realm"]

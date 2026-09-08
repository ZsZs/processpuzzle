# Stage 1: Build/Optimization stage
FROM quay.io/keycloak/keycloak:26.4.7 AS builder

# Enable health and metrics support
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

# ONE Keycloak container per environment, so there is no cluster to form — and the default
# distributed cache makes every start pay for that anyway. With JDBC_PING, each container records
# itself in the JGROUPS_PING table and never removes the row, so a redeploy leaves a dead peer
# behind: the next start spends its JOIN attempts on addresses that answer "Connection refused",
# ten tries at two seconds each, before logging "too many JOIN attempts (10): becoming singleton"
# and carrying on. The cost grows with every deployment, and once it exceeds the healthcheck's
# start_period compose declares the container unhealthy and takes down everything that waits on it
# — an infrastructure deployment that fails while Keycloak is, in fact, coming up fine.
#
# `cache` is a BUILD-TIME option, hence here and not in the compose file: `kc.sh start --optimized`
# exits 2 when a build-time option is supplied at run time (see docker-compose-infrastructure.yaml).
# The trade is HA, which a single-container deployment does not have to begin with. Restoring it
# means removing this line, rebuilding, and running more than one replica.
ENV KC_CACHE=local

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
# Deprecated KC_PROXY=edge replaced — see docker-compose-infrastructure.yaml for why this matters
# beyond the warning. A run-time option, so it is an overridable default here rather than baked.
ENV KC_PROXY_HEADERS=xforwarded
ENV KC_HOSTNAME_STRICT=false

# Expose ports: 8080 (HTTP), 8443 (HTTPS), 9000 (Management/Health)
EXPOSE 8080 8443 9000

# Start Keycloak with the 'optimized' flag and import any realm JSON in /opt/keycloak/data/import/
ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized", "--import-realm"]

# A one-shot container that runs the Admin CLI against a running Keycloak.
#
# Built from the Keycloak image itself rather than from a shell base plus curl: kcadm.sh ships with
# it, and reproducing what that script does with raw Admin API calls would mean hand-rolling token
# acquisition, refresh and the role-mapping payloads — the exact things this bootstrap must get right.
FROM quay.io/keycloak/keycloak:26.4.7

USER root
COPY tools/docker/keycloak/init/bootstrap-platform-admin-client.sh /opt/keycloak/bin/bootstrap-platform-admin-client.sh
RUN chmod +x /opt/keycloak/bin/bootstrap-platform-admin-client.sh
USER keycloak

ENTRYPOINT ["/opt/keycloak/bin/bootstrap-platform-admin-client.sh"]

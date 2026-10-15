package com.processpuzzle.workflow.definition.adapters.outbound;

import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.KeycloakTenantDirectory;
import com.processpuzzle.workflow.definition.usecases.outbound.NoOpRoleDirectoryPort;
import com.processpuzzle.workflow.definition.usecases.outbound.RoleDirectoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the {@link RoleDirectoryPort} implementation from configuration: a Keycloak adapter when an
 * admin secret is configured — Organization groups when {@code keycloak.admin.organization-realm}
 * is set, realm roles otherwise — and {@link NoOpRoleDirectoryPort} when it is not.
 *
 * <p>The decision is delegated to {@code KeycloakAdminClient.tenancy()} rather than re-reading
 * the property here, so no two features can disagree about whether an identity provider exists.
 * Verbatim the shape of org-admin's {@code UserDirectoryConfiguration}, including the warning: an
 * operator who forgot the secret otherwise discovers it as tokens that never carry the roles they
 * authored, with nothing in the log to say why.
 */
@Configuration
public class RoleDirectoryConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(RoleDirectoryConfiguration.class);

    @Bean
    public RoleDirectoryPort roleDirectoryPort(KeycloakAdminClient client, KeycloakTenantDirectory tenants) {
        return switch (client.tenancy()) {
            case UNCONFIGURED -> {
                LOG.warn("No keycloak.admin.client-secret configured: workflow role definitions will not "
                        + "be projected into realm roles. Role authoring still works, but no token will "
                        + "carry the roles it defines.");
                yield new NoOpRoleDirectoryPort();
            }
            case ORGANIZATION -> {
                LOG.info("Workflow roles are projected into Organization groups in realm '{}'.",
                        client.organizationRealm().orElse(""));
                yield new KeycloakOrganizationRoleDirectoryAdapter(client, tenants);
            }
            case REALM_PER_TENANT -> new KeycloakRoleDirectoryAdapter(client);
        };
    }
}

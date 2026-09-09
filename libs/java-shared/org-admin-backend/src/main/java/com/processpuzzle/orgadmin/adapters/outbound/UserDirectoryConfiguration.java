package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.orgadmin.usecases.outbound.NoOpUserDirectoryPort;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the {@link UserDirectoryPort} implementation from configuration: the Keycloak adapter when an
 * admin secret is configured, {@link NoOpUserDirectoryPort} otherwise.
 *
 * <p>The decision is delegated to {@code KeycloakAdminClient.isConfigured()} rather than re-reading
 * the property here, so the two modules cannot disagree about whether a directory exists — one
 * saying yes and the other no would produce a deployment that lists no users but accepts invitations.
 *
 * <p>Mirrors platform-admin's {@code IdentityRealmConfiguration}, including the warning: an operator
 * who forgot the secret otherwise discovers it as an empty user list with nothing in the log.
 */
@Configuration
public class UserDirectoryConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(UserDirectoryConfiguration.class);

    @Bean
    public UserDirectoryPort userDirectoryPort(KeycloakAdminClient client) {
        if (!client.isConfigured()) {
            LOG.warn("No keycloak.admin.client-secret configured: organization user management is "
                    + "unavailable. Reads answer empty and writes are refused with 503.");
            return new NoOpUserDirectoryPort();
        }
        return new KeycloakUserDirectoryAdapter(client);
    }
}

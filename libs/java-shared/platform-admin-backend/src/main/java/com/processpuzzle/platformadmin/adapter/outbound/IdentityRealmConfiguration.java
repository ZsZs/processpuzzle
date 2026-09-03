package com.processpuzzle.platformadmin.adapter.outbound;

import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import com.processpuzzle.platformadmin.usecase.port.NoOpIdentityRealmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the {@link IdentityRealmPort} implementation from configuration: the real Keycloak adapter
 * when {@code keycloak.admin.client-secret} is set, {@link NoOpIdentityRealmPort} otherwise.
 *
 * <p>An explicit factory method rather than {@code @ConditionalOnProperty} on two beans, and
 * rather than component-scanning both. Two reasons. First, the condition that matters is "is there a
 * usable secret", which is a property being non-blank rather than merely present — a
 * {@code KEYCLOAK_ADMIN_CLIENT_SECRET=} in a compose file sets the property to the empty string, and
 * a property-presence condition would happily select the real adapter. Second, this logs which way it
 * went: an operator who forgot the secret otherwise discovers it as tenants that never leave
 * {@code PROVISIONING}, with nothing in the log to say why.
 *
 * <p>A {@code @Bean} here loses to nothing: the use cases inject {@link IdentityRealmPort} directly,
 * so a deployment wanting a third implementation replaces this configuration rather than competing
 * with it.
 */
@Configuration
public class IdentityRealmConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityRealmConfiguration.class);

    @Bean
    public IdentityRealmPort identityRealmPort(KeycloakAdminProperties properties,
                                               KeycloakAdminClient client) {
        if (!properties.isConfigured()) {
            LOG.warn("No keycloak.admin.client-secret configured: tenant realms will NOT be created. "
                    + "Organizations still reach ACTIVE, but nothing can be logged into. Set the secret "
                    + "for any deployment that serves real users.");
            return new NoOpIdentityRealmPort();
        }
        LOG.info("Tenant realms will be managed in Keycloak at {} via client '{}'.",
                properties.getUrl(), properties.getClientId());
        return new KeycloakAdminAdapter(client, properties);
    }
}

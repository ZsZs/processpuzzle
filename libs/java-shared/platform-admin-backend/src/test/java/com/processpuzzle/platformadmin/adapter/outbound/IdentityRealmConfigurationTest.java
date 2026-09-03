package com.processpuzzle.platformadmin.adapter.outbound;

import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import com.processpuzzle.platformadmin.usecase.port.NoOpIdentityRealmPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdentityRealmConfigurationTest {

    @Test
    void usesNoOpPortWhenTheAdminSecretIsMissingOrBlank() {
        KeycloakAdminProperties properties = new KeycloakAdminProperties();
        IdentityRealmConfiguration configuration = new IdentityRealmConfiguration();
        KeycloakAdminClient client = mock(KeycloakAdminClient.class);

        assertThat(properties.isConfigured()).isFalse();
        assertThat(configuration.identityRealmPort(properties, client)).isInstanceOf(NoOpIdentityRealmPort.class);

        properties.setClientSecret("  ");
        assertThat(configuration.identityRealmPort(properties, client)).isInstanceOf(NoOpIdentityRealmPort.class);
    }

    @Test
    void usesKeycloakPortWhenTheAdminSecretIsConfigured() {
        KeycloakAdminProperties properties = new KeycloakAdminProperties();
        properties.setClientSecret("secret");

        IdentityRealmPort port = new IdentityRealmConfiguration()
                .identityRealmPort(properties, mock(KeycloakAdminClient.class));

        assertThat(port).isInstanceOf(KeycloakAdminAdapter.class);
    }

    @Test
    void noOpPortLeavesRealmsUntouchedAndReturnsDistinctUserIds() {
        NoOpIdentityRealmPort port = new NoOpIdentityRealmPort();

        port.createRealm("acme", "Acme Ltd.", "en");
        port.enableRealm("acme");
        port.disableRealm("acme");
        port.deleteRealm("acme");
        String first = port.createAdminUser("acme",
                new IdentityRealmPort.NewUser("ada", "ada@example.com", "Ada", "Lovelace"), List.of());
        String second = port.createAdminUser("acme",
                new IdentityRealmPort.NewUser("grace", "grace@example.com", "Grace", "Hopper"), List.of());

        assertThat(first).isNotEqualTo(second).matches("[0-9a-f-]{36}");
    }
}

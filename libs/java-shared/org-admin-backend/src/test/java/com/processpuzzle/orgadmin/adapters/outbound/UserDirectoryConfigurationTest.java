package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.orgadmin.usecases.outbound.NoOpUserDirectoryPort;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserDirectoryConfigurationTest {

    private final KeycloakAdminClient client = mock(KeycloakAdminClient.class);
    private final UserDirectoryConfiguration configuration = new UserDirectoryConfiguration();

    @Test
    void registersTheNoOpDirectoryWithoutAnAdminSecret() {
        when(client.isConfigured()).thenReturn(false);

        assertThat(configuration.userDirectoryPort(client)).isInstanceOf(NoOpUserDirectoryPort.class);
    }

    @Test
    void registersTheKeycloakDirectoryWithAnAdminSecret() {
        when(client.isConfigured()).thenReturn(true);

        assertThat(configuration.userDirectoryPort(client)).isInstanceOf(KeycloakUserDirectoryAdapter.class);
    }
}

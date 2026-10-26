package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.orgadmin.usecases.outbound.NoOpRoleCatalogPort;
import com.processpuzzle.orgadmin.usecases.outbound.NoOpUserDirectoryPort;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.KeycloakTenancy;
import com.processpuzzle.core.identity.KeycloakTenantDirectory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserDirectoryConfigurationTest {

    private final KeycloakAdminClient client = mock(KeycloakAdminClient.class);
    private final KeycloakTenantDirectory tenants = new KeycloakTenantDirectory(client);
    private final UserDirectoryConfiguration configuration = new UserDirectoryConfiguration();

    @Test
    void registersTheNoOpDirectoryWithoutAnAdminSecret() {
        when(client.tenancy()).thenReturn(KeycloakTenancy.UNCONFIGURED);

        assertThat(configuration.userDirectoryPort(client, tenants)).isInstanceOf(NoOpUserDirectoryPort.class);
    }

    @Test
    void registersTheKeycloakDirectoryWithAnAdminSecret() {
        when(client.tenancy()).thenReturn(KeycloakTenancy.REALM_PER_TENANT);

        assertThat(configuration.userDirectoryPort(client, tenants)).isInstanceOf(KeycloakUserDirectoryAdapter.class);
    }

    @Test
    void registersTheOrganizationDirectoryWithAnOrganizationRealm() {
        when(client.tenancy()).thenReturn(KeycloakTenancy.ORGANIZATION);
        when(client.organizationRealm()).thenReturn(Optional.of("shared"));

        UserDirectoryPort port = configuration.userDirectoryPort(client, tenants);

        assertThat(port).isInstanceOf(KeycloakOrganizationUserDirectoryAdapter.class);
    }

    @Test
    void keepsTheCatalogWhereTheTenancyKeepsItsRoles() {
        when(client.tenancy()).thenReturn(KeycloakTenancy.REALM_PER_TENANT);
        assertThat(configuration.roleCatalogPort(client, tenants)).isInstanceOf(KeycloakRealmRoleCatalogAdapter.class);

        when(client.tenancy()).thenReturn(KeycloakTenancy.ORGANIZATION);
        assertThat(configuration.roleCatalogPort(client, tenants)).isInstanceOf(KeycloakOrganizationRoleCatalogAdapter.class);

        when(client.tenancy()).thenReturn(KeycloakTenancy.UNCONFIGURED);
        assertThat(configuration.roleCatalogPort(client, tenants)).isInstanceOf(NoOpRoleCatalogPort.class);
    }
}

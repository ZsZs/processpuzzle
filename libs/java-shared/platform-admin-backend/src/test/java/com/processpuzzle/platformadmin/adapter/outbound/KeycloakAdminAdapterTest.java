package com.processpuzzle.platformadmin.adapter.outbound;

import com.processpuzzle.core.tenancy.TenantRoles;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.KeycloakAdminProperties;
import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakAdminAdapterTest {

    private KeycloakAdminClient client;
    private KeycloakAdminAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(KeycloakAdminClient.class);
        KeycloakAdminProperties properties = new KeycloakAdminProperties();
        properties.setTenantClientId("tenant-ui");
        properties.setTenantRedirectUri("https://app.example/{orgKey}/*");
        properties.setTenantWebOrigin("https://app.example");
        adapter = new KeycloakAdminAdapter(client, properties);
    }

    @Test
    void createsRealmClientAndBothOrganizationRoles() {
        adapter.createRealm("acme", "Acme Ltd.", "en-GB");

        ArgumentCaptor<Map<String, Object>> realm = mapCaptor();
        verify(client).exchangeTolerating(eq(HttpMethod.POST), eq("/admin/realms"), realm.capture(), eq(409));
        assertThat(realm.getValue()).containsEntry("realm", "acme")
                .containsEntry("displayName", "Acme Ltd.")
                .containsEntry("enabled", true)
                .containsEntry("internationalizationEnabled", true)
                .containsEntry("defaultLocale", "en");

        ArgumentCaptor<Map<String, Object>> tenantClient = mapCaptor();
        verify(client).exchangeTolerating(eq(HttpMethod.POST), eq("/admin/realms/acme/clients"),
                tenantClient.capture(), eq(409));
        assertThat(tenantClient.getValue()).containsEntry("clientId", "tenant-ui")
                .containsEntry("redirectUris", List.of("https://app.example/acme/*"))
                .containsEntry("webOrigins", List.of("https://app.example"));
        verify(client).exchangeTolerating(HttpMethod.POST, "/admin/realms/acme/roles",
                Map.of("name", TenantRoles.ORG_ADMIN,
                        "description", "May administer this organization's users and roles."), 409);
        verify(client).exchangeTolerating(HttpMethod.POST, "/admin/realms/acme/roles",
                Map.of("name", TenantRoles.ORG_MEMBER, "description", "Member of this organization."),
                409);
    }

    @Test
    void createsRealmWithoutLocaleWhenNoneIsSupplied() {
        adapter.createRealm("acme", "Acme Ltd.", " ");

        ArgumentCaptor<Map<String, Object>> realm = mapCaptor();
        verify(client).exchangeTolerating(eq(HttpMethod.POST), eq("/admin/realms"), realm.capture(), eq(409));
        assertThat(realm.getValue()).doesNotContainKeys("internationalizationEnabled", "defaultLocale");
    }

    @Test
    void updatesAndDeletesTheRealmUsingItsAdminPath() {
        adapter.enableRealm("acme");
        adapter.disableRealm("acme");
        adapter.deleteRealm("acme");

        verify(client).exchange(HttpMethod.PUT, "/admin/realms/acme",
                Map.of("realm", "acme", "enabled", true), null);
        verify(client).exchange(HttpMethod.PUT, "/admin/realms/acme",
                Map.of("realm", "acme", "enabled", false), null);
        verify(client).exchangeTolerating(HttpMethod.DELETE, "/admin/realms/acme", null, 404);
    }

    @Test
    void createsUserAndGrantsTheRolesThatExist() {
        IdentityRealmPort.NewUser user =
                new IdentityRealmPort.NewUser("ada", "ada@example.com", "Ada", "Lovelace");
        when(client.createAndReturnId(anyString(), any())).thenReturn(Optional.of("user-1"));
        when(client.exchange(eq(HttpMethod.GET), contains("roles/org-admin"), eq(null), eq(Map.class)))
                .thenReturn(Optional.of(Map.of("id", "role-1", "name", "org-admin")));
        when(client.exchange(eq(HttpMethod.GET), contains("roles/org-member"), eq(null), eq(Map.class)))
                .thenReturn(Optional.empty());

        assertThat(adapter.createAdminUser("acme", user, List.of("org-admin", "org-member"))).isEqualTo("user-1");

        verify(client).createAndReturnId(eq("/admin/realms/acme/users"), any());
        verify(client).exchange(HttpMethod.POST, "/admin/realms/acme/users/user-1/role-mappings/realm",
                List.of(Map.of("id", "role-1", "name", "org-admin")), null);
    }

    @Test
    void createsUserWithoutRolesAndReportsMissingReturnedId() {
        IdentityRealmPort.NewUser user =
                new IdentityRealmPort.NewUser("ada", "ada@example.com", "Ada", "Lovelace");
        when(client.createAndReturnId(anyString(), any())).thenReturn(Optional.of("user-1"));

        assertThat(adapter.createAdminUser("acme", user, null)).isEqualTo("user-1");
        verify(client, never()).exchange(eq(HttpMethod.GET), anyString(), any(), eq(Map.class));

        when(client.createAndReturnId(anyString(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adapter.createAdminUser("acme", user, List.of()))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageContaining("returned no id");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    private static String contains(String value) {
        return org.mockito.ArgumentMatchers.contains(value);
    }
}

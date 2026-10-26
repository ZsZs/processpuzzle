package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.identity.KeycloakAdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakRealmRoleCatalogAdapterTest {

    private static final String ROLES_PATH = "/admin/realms/acme/roles";

    private KeycloakAdminClient client;
    private KeycloakRealmRoleCatalogAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(KeycloakAdminClient.class);
        adapter = new KeycloakRealmRoleCatalogAdapter(client);
    }

    @Test
    void createsOnlyTheRolesTheRealmDoesNotDeclare() {
        when(client.getList(ROLES_PATH)).thenReturn(List.of(Map.of("name", "editor")));

        adapter.ensureRoles("acme", List.of("editor", "reviewer"), "Referenced by documents");

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(client).exchangeTolerating(eq(HttpMethod.POST), eq(ROLES_PATH), body.capture(),
                eq(KeycloakAdminClient.HTTP_CONFLICT));
        assertThat(body.getValue()).isEqualTo(Map.of("name", "reviewer", "description", "Referenced by documents"));
    }

    @Test
    void anAdministratorsRoleIsARealmRole() {
        adapter.createRole("acme", "claims-auditor", "Audits claims");

        verify(client).exchange(HttpMethod.POST, ROLES_PATH,
                Map.of("name", "claims-auditor", "description", "Audits claims"), null);
    }

    @Test
    void theDescriptionIsReplacedOnTheRoleOfThatName() {
        adapter.updateDescription("acme", "accounting", "Bookkeeping");

        verify(client).exchange(HttpMethod.PUT, ROLES_PATH + "/accounting",
                Map.of("name", "accounting", "description", "Bookkeeping"), null);
    }

    @Test
    void deletingTreatsAnAbsentRoleAsSuccess() {
        adapter.deleteRole("acme", "accounting");

        verify(client).exchangeTolerating(HttpMethod.DELETE, ROLES_PATH + "/accounting", null,
                KeycloakAdminClient.HTTP_NOT_FOUND);
    }

    @Test
    void aRoleIsHeldWhenAtLeastOneUserHasIt() {
        when(client.getList(ROLES_PATH + "/accounting/users?first=0&max=1")).thenReturn(List.of(Map.of("id", "u-1")));
        when(client.getList(ROLES_PATH + "/idle/users?first=0&max=1")).thenReturn(List.of());

        assertThat(adapter.hasHolders("acme", "accounting")).isTrue();
        assertThat(adapter.hasHolders("acme", "idle")).isFalse();
    }
}

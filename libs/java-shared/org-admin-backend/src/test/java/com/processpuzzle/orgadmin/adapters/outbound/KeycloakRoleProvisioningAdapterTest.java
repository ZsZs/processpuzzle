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

class KeycloakRoleProvisioningAdapterTest {

    private static final String ROLES_PATH = "/admin/realms/acme/roles";

    private KeycloakAdminClient client;
    private KeycloakRoleProvisioningAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(KeycloakAdminClient.class);
        adapter = new KeycloakRoleProvisioningAdapter(client);
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
}

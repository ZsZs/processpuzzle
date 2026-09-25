package com.processpuzzle.workflow.definition.adapters.outbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakOrganizationRoleDirectoryAdapterTest {

    private static final String GROUPS = "/admin/realms/shared/organizations/org-1/groups";

    private KeycloakAdminClient client;
    private KeycloakOrganizationRoleDirectoryAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(KeycloakAdminClient.class);
        adapter = new KeycloakOrganizationRoleDirectoryAdapter(client, "shared");
        when(client.getList("/admin/realms/shared/organizations?exact=true&search=acme"))
                .thenReturn(List.of(Map.of("id", "org-1", "alias", "acme")));
        when(client.getList(GROUPS + "?first=0&max=500")).thenReturn(List.of(
                Map.of("id", "g-reviewer", "name", "reviewer", "path", "/reviewer"),
                Map.of("id", "g-nested", "name", "approver", "path", "/reviewer/approver")));
    }

    @Test
    void rolesAreTheOrganizationsTopLevelGroups() {
        assertThat(adapter.findRoleNames("acme")).containsExactly("reviewer");
    }

    @Test
    void updatesAnExistingGroupAndCreatesAMissingOne() {
        adapter.upsertRole("acme", "reviewer", "Reviews");
        verify(client).exchange(eq(HttpMethod.PUT), eq(GROUPS + "/g-reviewer"), any(), eq(null));

        // A nested group of the same name is structure, not the role: the role is still created.
        adapter.upsertRole("acme", "approver", "Approves");
        verify(client).exchangeTolerating(eq(HttpMethod.POST), eq(GROUPS), any(), eq(409));
    }

    @Test
    void deletesOnlyAGroupThatIsThere() {
        adapter.deleteRole("acme", "reviewer");
        verify(client).exchange(HttpMethod.DELETE, GROUPS + "/g-reviewer", null, null);

        adapter.deleteRole("acme", "absent");
        verify(client, never()).exchange(eq(HttpMethod.DELETE), eq(GROUPS + "/absent"), any(), any());
    }

    @Test
    void anUnprovisionedOrganizationIsRetriedNotGuessed() {
        when(client.getList("/admin/realms/shared/organizations?exact=true&search=acm"))
                .thenReturn(List.of(Map.of("id", "org-1", "alias", "acme")));

        assertThatThrownBy(() -> adapter.upsertRole("acm", "reviewer", null))
                .isInstanceOf(IdentityProviderUnavailableException.class);
        verify(client, never()).exchangeTolerating(any(), anyString(), any(), any(int[].class));
    }
}

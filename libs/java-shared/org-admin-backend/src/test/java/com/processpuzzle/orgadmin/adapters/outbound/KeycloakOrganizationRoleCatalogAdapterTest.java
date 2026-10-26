package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.KeycloakTenantDirectory;
import com.processpuzzle.core.identity.TenantScope.OrganizationScope;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownOrganizationException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownRoleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KeycloakOrganizationRoleCatalogAdapterTest {

    private static final OrganizationScope ORG = new OrganizationScope("shared", "org-1", "acme");
    private static final String GROUPS = "/admin/realms/shared/organizations/org-1/groups";

    private KeycloakAdminClient client;
    private KeycloakTenantDirectory tenants;
    private KeycloakOrganizationRoleCatalogAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(KeycloakAdminClient.class);
        tenants = mock(KeycloakTenantDirectory.class);
        adapter = new KeycloakOrganizationRoleCatalogAdapter(client, tenants);
        when(tenants.organization("acme")).thenReturn(Optional.of(ORG));
        Map<String, String> groups = new LinkedHashMap<>();
        groups.put("accounting", "g-1");
        when(tenants.topLevelGroups(ORG)).thenReturn(groups);
    }

    /** A reference must not mint a role in a shared realm: the administrator defines it. */
    @Test
    void anAuthorsReferenceCreatesNothing() {
        adapter.ensureRoles("acme", List.of("editor"), "Referenced by documents");

        verifyNoInteractions(client);
    }

    @Test
    void aRoleIsATopLevelGroupOfTheOrganization() {
        adapter.createRole("acme", "claims-auditor", "Audits claims");

        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("name", "claims-auditor");
        representation.put("description", "Audits claims");
        verify(client).exchange(HttpMethod.POST, GROUPS, representation, null);
    }

    @Test
    void theDescriptionIsReplacedOnTheGroupOfThatName() {
        adapter.updateDescription("acme", "accounting", "Bookkeeping");

        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("name", "accounting");
        representation.put("description", "Bookkeeping");
        verify(client).exchange(HttpMethod.PUT, GROUPS + "/g-1", representation, null);
    }

    @Test
    void updatingARoleWithNoGroupIsRefusedRatherThanCreated() {
        assertThatThrownBy(() -> adapter.updateDescription("acme", "ghost", "x"))
                .isInstanceOf(UnknownRoleException.class);

        verify(client, never()).exchange(any(), anyString(), any(), any());
    }

    @Test
    void deletingRemovesTheGroupAndAnAbsentOneIsSuccess() {
        adapter.deleteRole("acme", "accounting");
        adapter.deleteRole("acme", "ghost");

        verify(client).exchange(HttpMethod.DELETE, GROUPS + "/g-1", null, null);
        verify(client, never()).exchange(HttpMethod.DELETE, GROUPS + "/null", null, null);
    }

    @Test
    void aRoleIsHeldWhenItsGroupHasAMember() {
        when(client.getList(GROUPS + "/g-1/members?first=0&max=1")).thenReturn(List.of(Map.of("id", "u-1")));

        assertThat(adapter.hasHolders("acme", "accounting")).isTrue();
    }

    @Test
    void aRoleWithNoMembersOrNoGroupIsNotHeld() {
        when(client.getList(GROUPS + "/g-1/members?first=0&max=1")).thenReturn(List.of());

        assertThat(adapter.hasHolders("acme", "accounting")).isFalse();
        assertThat(adapter.hasHolders("acme", "ghost")).isFalse();
    }

    @Test
    void anUnknownOrganizationIsReported() {
        when(tenants.organization("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.createRole("nobody", "x", null)).isInstanceOf(UnknownOrganizationException.class);
    }

    @Test
    void aProviderFailureIsReportedAsAnUnavailableDirectory() {
        when(client.exchange(any(), anyString(), any(), any()))
                .thenThrow(new IdentityProviderUnavailableException("down", new IllegalStateException()));

        assertThatThrownBy(() -> adapter.createRole("acme", "x", null)).isInstanceOf(DirectoryUnavailableException.class);
    }
}

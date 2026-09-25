package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownOrganizationException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserNotFoundException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakOrganizationUserDirectoryAdapterTest {

    private static final String REALM_PATH = "/admin/realms/shared";
    private static final String ORG_PATH = REALM_PATH + "/organizations/org-1";
    private static final String ALICE = "alice-id";

    private KeycloakAdminClient client;
    private KeycloakOrganizationUserDirectoryAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(KeycloakAdminClient.class);
        adapter = new KeycloakOrganizationUserDirectoryAdapter(client, "shared");
        when(client.getList(REALM_PATH + "/organizations?exact=true&search=acme"))
                .thenReturn(List.of(Map.of("id", "org-1", "alias", "acme")));
        when(client.getList(REALM_PATH + "/roles"))
                .thenReturn(List.of(role("org-admin"), role("org-member"), role("offline_access")));
        when(client.getList(ORG_PATH + "/groups?first=0&max=500"))
                .thenReturn(List.of(group("reviewer", "/reviewer"), group("approver", "/approver"),
                        group("backend", "/reviewer/backend")));
    }

    @Test
    void listsOnlyTheOrganizationsMembersWithPlatformRolesAndRoleGroups() {
        when(client.getList(ORG_PATH + "/members?first=0&max=20&search=Ada+L"))
                .thenReturn(List.of(user(ALICE)));
        givenRoles(ALICE, List.of(role("org-member"), role("offline_access")),
                List.of(group("reviewer", "/reviewer"), group("backend", "/reviewer/backend")));

        DirectoryPage page = adapter.findUsers("acme", "Ada L", 0, 20);

        assertThat(page.content()).extracting(DirectoryUser::id).containsExactly(ALICE);
        assertThat(page.content().getFirst().roles()).containsExactly("org-member", "reviewer");
        verify(client, never()).getList(startsWith(REALM_PATH + "/users?"));
    }

    @Test
    void anAliasNoOrganizationCarriesIsAnUnknownOrganization() {
        when(client.getList(REALM_PATH + "/organizations?exact=true&search=acm"))
                .thenReturn(List.of(Map.of("id", "org-1", "alias", "acme")));

        assertThatThrownBy(() -> adapter.findUsers("acm", null, 0, 20))
                .isInstanceOf(UnknownOrganizationException.class);
    }

    @Test
    void anotherTenantsUserIsNotFoundAndNeverWritten() {
        givenNotAMember("mallory-id");

        assertThat(adapter.findUser("acme", "mallory-id")).isEmpty();
        UserDirectoryPort.UserProfile profile = new UserDirectoryPort.UserProfile("m@x", "M", "X", false);
        assertThatThrownBy(() -> adapter.updateUser("acme", "mallory-id", profile))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> adapter.deleteUser("acme", "mallory-id"))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> adapter.replaceRoles("acme", "mallory-id", List.of("org-admin")))
                .isInstanceOf(UserNotFoundException.class);

        verify(client, never()).exchange(eq(HttpMethod.PUT), anyString(), any(), eq(null));
        verify(client, never()).exchange(eq(HttpMethod.DELETE), anyString(), any(), eq(null));
        verify(client, never()).exchange(eq(HttpMethod.POST), anyString(), any(), eq(null));
    }

    @Test
    void invitesIntoTheOrganizationGrantingPlatformRolesAndRoleGroups() {
        when(client.createAndReturnId(eq(REALM_PATH + "/users"), any())).thenReturn(Optional.of(ALICE));
        givenMember(ALICE);
        givenRoles(ALICE, List.of(role("org-member")), List.of(group("reviewer", "/reviewer")));

        DirectoryUser invited = adapter.inviteUser("acme",
                new UserDirectoryPort.NewUser("alice", "alice@acme", "Alice", "A"), List.of("reviewer", "org-member"));

        assertThat(invited.id()).isEqualTo(ALICE);
        verify(client).exchange(HttpMethod.POST, ORG_PATH + "/members", "\"" + ALICE + "\"", null);
        verify(client).exchange(HttpMethod.POST, REALM_PATH + "/users/" + ALICE + "/role-mappings/realm",
                List.of(Map.of("id", "org-member-id", "name", "org-member")), null);
        verify(client).exchange(HttpMethod.PUT, ORG_PATH + "/groups/reviewer-id/members/" + ALICE, null, null);
    }

    @Test
    void undoesTheAccountWhenTheMembershipCannotBeAdded() {
        when(client.createAndReturnId(eq(REALM_PATH + "/users"), any())).thenReturn(Optional.of(ALICE));
        doThrow(new IdentityProviderUnavailableException("down", new IllegalStateException()))
                .when(client).exchange(HttpMethod.POST, ORG_PATH + "/members", "\"" + ALICE + "\"", null);

        assertThatThrownBy(() -> adapter.inviteUser("acme",
                new UserDirectoryPort.NewUser("alice", "alice@acme", null, null), List.of()))
                .isInstanceOf(DirectoryUnavailableException.class);
        verify(client).exchange(HttpMethod.DELETE, REALM_PATH + "/users/" + ALICE, null, null);
    }

    @Test
    void aDuplicateUsernameIsAConflictNotAnOutage() {
        doThrow(new IdentityProviderUnavailableException("create failed",
                HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null)))
                .when(client).createAndReturnId(anyString(), any());

        assertThatThrownBy(() -> adapter.inviteUser("acme",
                new UserDirectoryPort.NewUser("alice", "alice@acme", null, null), List.of()))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void deletesTheAccountOnlyWhenNoOtherOrganizationHasIt() {
        givenMember(ALICE);
        when(client.getList(REALM_PATH + "/organizations/members/" + ALICE + "/organizations")).thenReturn(List.of());

        adapter.deleteUser("acme", ALICE);

        verify(client).exchange(HttpMethod.DELETE, ORG_PATH + "/members/" + ALICE, null, null);
        verify(client).exchange(HttpMethod.DELETE, REALM_PATH + "/users/" + ALICE, null, null);
    }

    @Test
    void keepsTheAccountThatAnotherOrganizationStillHas() {
        givenMember(ALICE);
        when(client.getList(REALM_PATH + "/organizations/members/" + ALICE + "/organizations"))
                .thenReturn(List.of(Map.of("id", "org-2", "alias", "other")));

        adapter.deleteUser("acme", ALICE);

        verify(client).exchange(HttpMethod.DELETE, ORG_PATH + "/members/" + ALICE, null, null);
        verify(client, never()).exchange(HttpMethod.DELETE, REALM_PATH + "/users/" + ALICE, null, null);
    }

    @Test
    void offersPlatformRolesAndTopLevelGroupsOnly() {
        assertThat(adapter.findRoles("acme"))
                .extracting(DirectoryRole::name, DirectoryRole::platformManaged)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("org-admin", true),
                        org.assertj.core.groups.Tuple.tuple("org-member", true),
                        org.assertj.core.groups.Tuple.tuple("reviewer", false),
                        org.assertj.core.groups.Tuple.tuple("approver", false));
    }

    @Test
    void replacesRolesRevokingBeforeGranting() {
        givenMember(ALICE);
        givenRoles(ALICE, List.of(role("org-admin"), role("org-member")), List.of(group("reviewer", "/reviewer")));

        adapter.replaceRoles("acme", ALICE, List.of("org-member", "approver"));

        verify(client).exchange(HttpMethod.DELETE, REALM_PATH + "/users/" + ALICE + "/role-mappings/realm",
                List.of(Map.of("id", "org-admin-id", "name", "org-admin")), null);
        verify(client).exchange(HttpMethod.DELETE, ORG_PATH + "/groups/reviewer-id/members/" + ALICE, null, null);
        verify(client).exchange(HttpMethod.PUT, ORG_PATH + "/groups/approver-id/members/" + ALICE, null, null);
        verify(client, never()).exchange(eq(HttpMethod.POST), eq(REALM_PATH + "/users/" + ALICE + "/role-mappings/realm"),
                any(), eq(null));
    }

    // --- fixtures ------------------------------------------------------------------------

    private void givenMember(String userId) {
        when(client.exchange(HttpMethod.GET, ORG_PATH + "/members/" + userId, null, Map.class))
                .thenReturn(Optional.of(user(userId)));
    }

    private void givenNotAMember(String userId) {
        doThrow(new IdentityProviderUnavailableException("Keycloak admin call failed",
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null)))
                .when(client).exchange(HttpMethod.GET, ORG_PATH + "/members/" + userId, null, Map.class);
    }

    private void givenRoles(String userId, List<Map<String, Object>> realmRoles, List<Map<String, Object>> groups) {
        when(client.getList(REALM_PATH + "/users/" + userId + "/role-mappings/realm")).thenReturn(realmRoles);
        when(client.getList(ORG_PATH + "/members/" + userId + "/groups")).thenReturn(groups);
    }

    private static Map<String, Object> user(String id) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("username", "alice");
        user.put("email", "alice@acme");
        user.put("enabled", true);
        user.put("emailVerified", true);
        return user;
    }

    private static Map<String, Object> role(String name) {
        return Map.of("id", name + "-id", "name", name);
    }

    private static Map<String, Object> group(String name, String path) {
        return Map.of("id", name + "-id", "name", name, "path", path);
    }
}

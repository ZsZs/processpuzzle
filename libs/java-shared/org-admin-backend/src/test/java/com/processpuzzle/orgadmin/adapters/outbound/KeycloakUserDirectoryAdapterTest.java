package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakUserDirectoryAdapterTest {

    private static final String REALM = "acme";
    private static final String USER_ID = "user-1";

    private KeycloakAdminClient client;
    private KeycloakUserDirectoryAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(KeycloakAdminClient.class);
        adapter = new KeycloakUserDirectoryAdapter(client);
    }

    @Test
    void findsUsersWithEncodedSearchRolesAndAnHonestNextPageEstimate() {
        when(client.getList(anyString())).thenReturn(List.of(role("org-member")));
        when(client.getList("/admin/realms/acme/users?first=5&max=5&search=Ada+Lovelace"))
                .thenReturn(List.of(user("user-1", true, 1000L), user("user-2", false, null),
                        user("user-3", true, 2000L), user("user-4", true, 3000L),
                        user("user-5", true, 4000L)));

        DirectoryPage page = adapter.findUsers(REALM, "Ada Lovelace", 1, 5);

        assertThat(page.totalElements()).isEqualTo(11);
        assertThat(page.content()).extracting(DirectoryUser::id).containsExactly(
                "user-1", "user-2", "user-3", "user-4", "user-5");
        assertThat(page.content().getFirst()).extracting(
                DirectoryUser::enabled, DirectoryUser::emailVerified, DirectoryUser::createdAt, DirectoryUser::roles)
                .containsExactly(true, false, java.time.Instant.ofEpochMilli(1000L), List.of("org-member"));
        assertThat(page.content().get(1).createdAt()).isNull();
    }

    @Test
    void findsAUserAndLeavesOptionalValuesEmptyWhenKeycloakDoes() {
        when(client.exchange(HttpMethod.GET, "/admin/realms/acme/users/user-1", null, Map.class))
                .thenReturn(Optional.of(user("user-1", false, null)));
        when(client.getList("/admin/realms/acme/users/user-1/role-mappings/realm")).thenReturn(List.of());

        assertThat(adapter.findUser(REALM, USER_ID).orElseThrow()).satisfies(found -> {
            assertThat(found.username()).isEqualTo("ada");
            assertThat(found.createdAt()).isNull();
            assertThat(found.roles()).isEmpty();
        });

        when(client.exchange(HttpMethod.GET, "/admin/realms/acme/users/missing", null, Map.class))
                .thenReturn(Optional.empty());
        assertThat(adapter.findUser(REALM, "missing")).isEmpty();
    }

    @Test
    void invitesUserGrantsExistingRolesAndReadsTheStoredResult() {
        UserDirectoryPort.NewUser user =
                new UserDirectoryPort.NewUser("ada", "ada@example.com", "Ada", "Lovelace");
        when(client.createAndReturnId(anyString(), any())).thenReturn(Optional.of(USER_ID));
        when(client.exchange(HttpMethod.GET, "/admin/realms/acme/roles/org-member", null, Map.class))
                .thenReturn(Optional.of(role("org-member")));
        when(client.exchange(HttpMethod.GET, "/admin/realms/acme/users/user-1", null, Map.class))
                .thenReturn(Optional.of(user(USER_ID, true, 1000L)));
        when(client.getList("/admin/realms/acme/users/user-1/role-mappings/realm"))
                .thenReturn(List.of(role("org-member")));

        assertThat(adapter.inviteUser(REALM, user, List.of("org-member")).username()).isEqualTo("ada");

        ArgumentCaptor<Map<String, Object>> representation = mapCaptor();
        verify(client).createAndReturnId(eq("/admin/realms/acme/users"), representation.capture());
        assertThat(representation.getValue()).containsEntry("username", "ada")
                .containsEntry("enabled", true)
                .containsEntry("requiredActions", List.of("UPDATE_PASSWORD"));
        verify(client).exchange(HttpMethod.POST, "/admin/realms/acme/users/user-1/role-mappings/realm",
                List.of(Map.of("id", "org-member-id", "name", "org-member")), null);
    }

    @Test
    void reportsFailedOrUnreadableInvitationsPrecisely() {
        UserDirectoryPort.NewUser user = new UserDirectoryPort.NewUser("ada", "ada@example.com", null, null);
        when(client.createAndReturnId(anyString(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adapter.inviteUser(REALM, user, List.of()))
                .isInstanceOf(DirectoryUnavailableException.class)
                .hasMessageContaining("returned no id");

        when(client.createAndReturnId(anyString(), any())).thenReturn(Optional.of(USER_ID));
        when(client.exchange(HttpMethod.GET, "/admin/realms/acme/users/user-1", null, Map.class))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> adapter.inviteUser(REALM, user, List.of()))
                .isInstanceOf(DirectoryUnavailableException.class)
                .hasMessageContaining("will not read it back");
    }

    @Test
    void translatesDuplicateAndTransportFailures() {
        UserDirectoryPort.NewUser user = new UserDirectoryPort.NewUser("ada", "ada@example.com", null, null);
        doThrow(unavailable("409 Conflict")).when(client).createAndReturnId(anyString(), any());
        assertThatThrownBy(() -> adapter.inviteUser(REALM, user, List.of()))
                .isInstanceOf(UserAlreadyExistsException.class);

        doThrow(unavailable("down")).when(client).createAndReturnId(anyString(), any());
        assertThatThrownBy(() -> adapter.inviteUser(REALM, user, List.of()))
                .isInstanceOf(DirectoryUnavailableException.class)
                .hasCauseInstanceOf(IdentityProviderUnavailableException.class);

        when(client.getList(anyString())).thenThrow(unavailable("down"));
        assertThatThrownBy(() -> adapter.findRoles(REALM))
                .isInstanceOf(DirectoryUnavailableException.class);
    }

    @Test
    void updatesAndDeletesUsersThroughTheirAdminPaths() {
        UserDirectoryPort.UserProfile profile =
                new UserDirectoryPort.UserProfile("new@example.com", "Ada", "Byron", null);
        when(client.exchange(HttpMethod.GET, "/admin/realms/acme/users/user-1", null, Map.class))
                .thenReturn(Optional.of(user(USER_ID, true, 1000L)));
        when(client.getList("/admin/realms/acme/users/user-1/role-mappings/realm")).thenReturn(List.of());

        assertThat(adapter.updateUser(REALM, USER_ID, profile).email()).isEqualTo("ada@example.com");
        verify(client).exchange(HttpMethod.PUT, "/admin/realms/acme/users/user-1",
                Map.of("email", "new@example.com", "firstName", "Ada", "lastName", "Byron"), null);
        adapter.deleteUser(REALM, USER_ID);
        verify(client).exchange(HttpMethod.DELETE, "/admin/realms/acme/users/user-1", null, null);
    }

    @Test
    void updatesIncludeEnabledWhenSpecifiedAndReportDisappearingUsers() {
        UserDirectoryPort.UserProfile profile =
                new UserDirectoryPort.UserProfile("new@example.com", "Ada", "Byron", false);
        when(client.exchange(HttpMethod.GET, "/admin/realms/acme/users/user-1", null, Map.class))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.updateUser(REALM, USER_ID, profile))
                .isInstanceOf(DirectoryUnavailableException.class)
                .hasMessageContaining("disappeared");
        verify(client).exchange(HttpMethod.PUT, "/admin/realms/acme/users/user-1",
                Map.of("email", "new@example.com", "firstName", "Ada", "lastName", "Byron", "enabled", false),
                null);
    }

    @Test
    void listsRolesAndReplacesOnlyChangedExistingMappings() {
        when(client.getList("/admin/realms/acme/roles"))
                .thenReturn(List.of(role("org-admin"), Map.of("name", "auditor", "description", "Can audit")));
        assertThat(adapter.findRoles(REALM)).containsExactly(
                new DirectoryRole("org-admin", null, true),
                new DirectoryRole("auditor", "Can audit", false));

        when(client.getList("/admin/realms/acme/users/user-1/role-mappings/realm"))
                .thenReturn(List.of(role("old"), role("keep")))
                .thenReturn(List.of(role("keep"), role("new")));
        when(client.exchange(HttpMethod.GET, "/admin/realms/acme/roles/old", null, Map.class))
                .thenReturn(Optional.of(role("old")));
        when(client.exchange(HttpMethod.GET, "/admin/realms/acme/roles/new", null, Map.class))
                .thenReturn(Optional.of(role("new")));

        assertThat(adapter.replaceRoles(REALM, USER_ID, List.of("keep", "new")))
                .extracting(DirectoryRole::name).containsExactly("keep", "new");
        verify(client).exchange(HttpMethod.DELETE, "/admin/realms/acme/users/user-1/role-mappings/realm",
                List.of(Map.of("id", "old-id", "name", "old")), null);
        verify(client).exchange(HttpMethod.POST, "/admin/realms/acme/users/user-1/role-mappings/realm",
                List.of(Map.of("id", "new-id", "name", "new")), null);
    }

    @Test
    void skipsMissingRolesAndDoesNotWriteEmptyMappings() {
        when(client.getList("/admin/realms/acme/users/user-1/role-mappings/realm")).thenReturn(List.of());
        when(client.exchange(HttpMethod.GET, "/admin/realms/acme/roles/missing", null, Map.class))
                .thenReturn(Optional.empty());

        assertThat(adapter.replaceRoles(REALM, USER_ID, List.of("missing"))).isEmpty();
        verify(client, never()).exchange(eq(HttpMethod.POST), anyString(), any(), eq(null));
        verify(client, never()).exchange(eq(HttpMethod.DELETE), anyString(), any(), eq(null));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    private static Map<String, Object> user(String id, boolean enabled, Long createdTimestamp) {
        Map<String, Object> user = new java.util.LinkedHashMap<>();
        user.put("id", id);
        user.put("username", "ada");
        user.put("email", "ada@example.com");
        user.put("firstName", "Ada");
        user.put("lastName", "Lovelace");
        user.put("enabled", enabled);
        user.put("emailVerified", false);
        if (createdTimestamp != null) {
            user.put("createdTimestamp", createdTimestamp);
        }
        return user;
    }

    private static Map<String, Object> role(String name) {
        return Map.of("id", name + "-id", "name", name);
    }

    private static IdentityProviderUnavailableException unavailable(String message) {
        return new IdentityProviderUnavailableException("Keycloak failed",
                new IllegalStateException(message));
    }
}

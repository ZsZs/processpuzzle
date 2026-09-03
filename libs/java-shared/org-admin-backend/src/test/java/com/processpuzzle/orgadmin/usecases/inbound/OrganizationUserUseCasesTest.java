package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.OrgAdminTestFixtures;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserNotFoundException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.ORG_KEY;
import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationUserUseCasesTest {

    private static final UserDirectoryPort.NewUser NEW_USER =
            new UserDirectoryPort.NewUser("ada", "ada@my-org.example", "Ada", "Lovelace");

    private UserDirectoryPort directory;
    private TenantRealmResolver realms;

    @BeforeEach
    void setUp() {
        directory = mock(UserDirectoryPort.class);
        realms = OrgAdminTestFixtures.resolver();
        when(directory.findUsers(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(new DirectoryPage(List.of(OrgAdminTestFixtures.user("org-member")), 1L, 0, 20));
        when(directory.findUser(anyString(), anyString()))
                .thenReturn(Optional.of(OrgAdminTestFixtures.user("org-member")));
        when(directory.inviteUser(anyString(), any(), anyList()))
                .thenReturn(OrgAdminTestFixtures.user("org-member"));
        when(directory.updateUser(anyString(), anyString(), any()))
                .thenReturn(OrgAdminTestFixtures.user("org-member"));
    }

    @Test
    void listingPagesThroughTheDirectoryUnderTheTenantsRealm() {
        DirectoryPage page = new FindOrganizationUsers(realms, directory)
                .execute(ORG_KEY, "ada", 1, 5);

        assertThat(page.content()).hasSize(1);
        verify(directory).findUsers(ORG_KEY, "ada", 1, 5);
    }

    @Test
    void absentPagingFallsBackToTheFirstPageOfTwenty() {
        new FindOrganizationUsers(realms, directory).execute(ORG_KEY, null, null, null);

        verify(directory).findUsers(ORG_KEY, null, 0, 20);
    }

    @Test
    void readingOneUserAnswersIt() {
        assertThat(new FindOrganizationUser(realms, directory).execute(ORG_KEY, USER_ID).username())
                .isEqualTo("ada");
    }

    @Test
    void readingAnAbsentUserIs404() {
        when(directory.findUser(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new FindOrganizationUser(realms, directory).execute(ORG_KEY, "nope"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("nope");
    }

    /**
     * {@code org-member} is added whether or not it was asked for. Nav visibility and workflow role
     * assignment match on it, so a user invited with only a specialised role would be invisible to
     * every one of those checks.
     */
    @Test
    void anInvitationAlwaysCarriesTheMemberRole() {
        new InviteOrganizationUser(realms, directory).execute(ORG_KEY, NEW_USER, List.of("claims-auditor"));

        assertThat(rolesPassedToInvite()).containsExactly("claims-auditor", "org-member");
    }

    @Test
    void anInvitationWithNoRolesGetsTheMemberRoleAlone() {
        new InviteOrganizationUser(realms, directory).execute(ORG_KEY, NEW_USER, null);

        assertThat(rolesPassedToInvite()).containsExactly("org-member");
    }

    /** Naming it explicitly must not grant it twice. */
    @Test
    void namingTheMemberRoleExplicitlyDoesNotDuplicateIt() {
        new InviteOrganizationUser(realms, directory)
                .execute(ORG_KEY, NEW_USER, List.of("org-member", "claims-auditor"));

        assertThat(rolesPassedToInvite()).containsExactly("org-member", "claims-auditor");
    }

    @Test
    void blankRoleNamesAreDropped() {
        new InviteOrganizationUser(realms, directory)
                .execute(ORG_KEY, NEW_USER, java.util.Arrays.asList("  ", null, "claims-auditor"));

        assertThat(rolesPassedToInvite()).containsExactly("claims-auditor", "org-member");
    }

    @Test
    void updatingForwardsTheProfileAndAnswersTheStoredUser() {
        var profile = new UserDirectoryPort.UserProfile("new@my-org.example", "Ada", "L", false);

        assertThat(new UpdateOrganizationUser(realms, directory)
                .execute(ORG_KEY, USER_ID, profile).username()).isEqualTo("ada");
        verify(directory).updateUser(ORG_KEY, USER_ID, profile);
    }

    /** Reported the same way a read of a nonexistent user is, rather than as the adapter's 404. */
    @Test
    void updatingAnAbsentUserIs404AndWritesNothing() {
        when(directory.findUser(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new UpdateOrganizationUser(realms, directory).execute(
                ORG_KEY, "nope", new UserDirectoryPort.UserProfile(null, null, null, null)))
                .isInstanceOf(UserNotFoundException.class);

        verify(directory, never()).updateUser(anyString(), anyString(), any());
    }

    @Test
    void deletingRemovesTheUser() {
        new DeleteOrganizationUser(realms, directory).execute(ORG_KEY, USER_ID);

        verify(directory).deleteUser(ORG_KEY, USER_ID);
    }

    @Test
    void deletingAnAbsentUserIs404AndDeletesNothing() {
        when(directory.findUser(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeleteOrganizationUser(realms, directory).execute(ORG_KEY, "nope"))
                .isInstanceOf(UserNotFoundException.class);

        verify(directory, never()).deleteUser(anyString(), anyString());
    }

    @SuppressWarnings("unchecked")
    private List<String> rolesPassedToInvite() {
        ArgumentCaptor<List<String>> roles = ArgumentCaptor.forClass(List.class);
        verify(directory).inviteUser(anyString(), any(), roles.capture());
        return roles.getValue();
    }
}

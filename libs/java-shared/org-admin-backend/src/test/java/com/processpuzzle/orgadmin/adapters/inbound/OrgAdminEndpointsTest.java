package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.orgadmin.OrgAdminTestFixtures;
import com.processpuzzle.orgadmin.model.OrganizationUserInvite;
import com.processpuzzle.orgadmin.model.OrganizationUserUpdate;
import com.processpuzzle.orgadmin.model.RoleAssignment;
import com.processpuzzle.orgadmin.usecases.inbound.DeleteOrganizationUser;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationRoles;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationUser;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationUserRoles;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationUsers;
import com.processpuzzle.orgadmin.usecases.inbound.InviteOrganizationUser;
import com.processpuzzle.orgadmin.usecases.inbound.ReplaceOrganizationUserRoles;
import com.processpuzzle.orgadmin.usecases.inbound.UpdateOrganizationUser;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.ORG_KEY;
import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The controllers hold no logic, so what is worth pinning down is the status codes the contract
 * declares and that each payload reaches its use case as the mapped value rather than as a DTO.
 */
class OrgAdminEndpointsTest {

    private final FindOrganizationUsers findUsers = mock(FindOrganizationUsers.class);
    private final FindOrganizationUser findUser = mock(FindOrganizationUser.class);
    private final InviteOrganizationUser inviteUser = mock(InviteOrganizationUser.class);
    private final UpdateOrganizationUser updateUser = mock(UpdateOrganizationUser.class);
    private final DeleteOrganizationUser deleteUser = mock(DeleteOrganizationUser.class);
    private final FindOrganizationRoles findRoles = mock(FindOrganizationRoles.class);
    private final FindOrganizationUserRoles findUserRoles = mock(FindOrganizationUserRoles.class);
    private final ReplaceOrganizationUserRoles replaceRoles = mock(ReplaceOrganizationUserRoles.class);

    private final OrganizationUserEndpoint users = new OrganizationUserEndpoint(
            findUsers, findUser, inviteUser, updateUser, deleteUser, new OrgAdminMapper());
    private final OrganizationRoleEndpoint roles = new OrganizationRoleEndpoint(
            findRoles, findUserRoles, replaceRoles, new OrgAdminMapper());

    @Test
    void listingAnswersThePage() {
        when(findUsers.execute(ORG_KEY, "ada", 0, 20)).thenReturn(
                new DirectoryPage(List.of(OrgAdminTestFixtures.user("org-member")), 1L, 0, 20));

        var response = users.listOrganizationUsers(ORG_KEY, "ada", 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
    }

    @Test
    void readingOneUserAnswersIt() {
        when(findUser.execute(ORG_KEY, USER_ID)).thenReturn(OrgAdminTestFixtures.user());

        assertThat(users.getOrganizationUser(ORG_KEY, USER_ID).getBody()).isNotNull()
                .satisfies(body -> assertThat(body.getUsername()).isEqualTo("ada"));
    }

    /** 201, and the roles from the payload reach the use case rather than being dropped. */
    @Test
    void invitingAnswers201AndForwardsTheRequestedRoles() {
        when(inviteUser.execute(any(), any(), any())).thenReturn(OrgAdminTestFixtures.user("org-member"));
        OrganizationUserInvite input = new OrganizationUserInvite("ada", "ada@my-org.example");
        input.setRoles(List.of("claims-auditor"));

        var response = users.inviteOrganizationUser(ORG_KEY, input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(inviteUser).execute(ORG_KEY,
                new UserDirectoryPort.NewUser("ada", "ada@my-org.example", null, null),
                List.of("claims-auditor"));
    }

    @Test
    void updatingForwardsTheMappedProfile() {
        when(updateUser.execute(any(), any(), any())).thenReturn(OrgAdminTestFixtures.user());
        OrganizationUserUpdate input = new OrganizationUserUpdate();
        input.setEnabled(false);

        assertThat(users.updateOrganizationUser(ORG_KEY, USER_ID, input).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        verify(updateUser).execute(ORG_KEY, USER_ID,
                new UserDirectoryPort.UserProfile(null, null, null, false));
    }

    @Test
    void deletingAnswers204WithNoBody() {
        var response = users.deleteOrganizationUser(ORG_KEY, USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(deleteUser).execute(ORG_KEY, USER_ID);
    }

    @Test
    void theRealmsRolesAreAnswered() {
        when(findRoles.execute(ORG_KEY)).thenReturn(List.of(OrgAdminTestFixtures.role("org-admin")));

        assertThat(roles.listOrganizationRoles(ORG_KEY).getBody()).isNotNull().hasSize(1);
    }

    @Test
    void aUsersRolesAreAnswered() {
        when(findUserRoles.execute(ORG_KEY, USER_ID))
                .thenReturn(List.of(OrgAdminTestFixtures.role("org-member")));

        assertThat(roles.getOrganizationUserRoles(ORG_KEY, USER_ID).getBody()).isNotNull().hasSize(1);
    }

    @Test
    void replacingRolesForwardsTheWholeSetAndAnswersWhatTheUserHoldsAfterwards() {
        when(replaceRoles.execute(ORG_KEY, USER_ID, List.of("org-member")))
                .thenReturn(List.of(OrgAdminTestFixtures.role("org-member")));

        var response = roles.replaceOrganizationUserRoles(
                ORG_KEY, USER_ID, new RoleAssignment(List.of("org-member")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        verify(replaceRoles).execute(ORG_KEY, USER_ID, List.of("org-member"));
    }
}

package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.orgadmin.OrgAdminTestFixtures;
import com.processpuzzle.orgadmin.model.NewOrganizationRole;
import com.processpuzzle.orgadmin.model.OrganizationRole;
import com.processpuzzle.orgadmin.model.OrganizationRoleUpdate;
import com.processpuzzle.orgadmin.model.OrganizationUserInvite;
import com.processpuzzle.orgadmin.model.OrganizationUserUpdate;
import com.processpuzzle.orgadmin.model.RoleAssignment;
import com.processpuzzle.orgadmin.usecases.inbound.CatalogRole;
import com.processpuzzle.orgadmin.usecases.inbound.CreateOrganizationRole;
import com.processpuzzle.orgadmin.usecases.inbound.DeleteOrganizationRole;
import com.processpuzzle.orgadmin.usecases.inbound.DeleteOrganizationUser;
import com.processpuzzle.orgadmin.usecases.inbound.RoleOrigin;
import com.processpuzzle.orgadmin.usecases.inbound.UpdateOrganizationRole;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationRoles;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationUser;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationUserRoles;
import com.processpuzzle.orgadmin.usecases.inbound.FindOrganizationUsers;
import com.processpuzzle.orgadmin.usecases.inbound.InviteOrganizationUser;
import com.processpuzzle.orgadmin.usecases.inbound.ReplaceOrganizationUserRoles;
import com.processpuzzle.orgadmin.usecases.inbound.UpdateOrganizationUser;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.InvitationRecoveryRequiredException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.ORG_KEY;
import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private final CreateOrganizationRole createRole = mock(CreateOrganizationRole.class);
    private final UpdateOrganizationRole updateRole = mock(UpdateOrganizationRole.class);
    private final DeleteOrganizationRole deleteRole = mock(DeleteOrganizationRole.class);
    private final FindOrganizationRoles findRoles = mock(FindOrganizationRoles.class);
    private final FindOrganizationUserRoles findUserRoles = mock(FindOrganizationUserRoles.class);
    private final ReplaceOrganizationUserRoles replaceRoles = mock(ReplaceOrganizationUserRoles.class);

    private final OrganizationUserEndpoint users = new OrganizationUserEndpoint(
            findUsers, findUser, inviteUser, updateUser, deleteUser, new OrgAdminMapper());
    private final OrganizationRoleEndpoint roles = new OrganizationRoleEndpoint(
            createRole, updateRole, deleteRole, findRoles, findUserRoles, replaceRoles, new OrgAdminMapper());

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

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"created-id"})
    void invitationRecoveryReachesTheClientWithItsCorrelationId(String userId) throws Exception {
        when(inviteUser.execute(any(), any(), any())).thenThrow(
                new InvitationRecoveryRequiredException("operation-1", userId,
                        new DirectoryUnavailableException("provider outcome unknown")));
        var mvc = MockMvcBuilders.standaloneSetup(users)
                .setControllerAdvice(new OrgAdminApiExceptionHandler()).build();

        var response = mvc.perform(post("/organizations/" + ORG_KEY + "/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ada\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorId").value("user.invitation-recovery-required"))
                .andExpect(jsonPath("$.operationId").value("operation-1"))
                .andExpect(jsonPath("$.errorText").value(
                        org.hamcrest.Matchers.containsString("before retrying")));
        if (userId == null) {
            response.andExpect(jsonPath("$.userId").doesNotExist());
        } else {
            response.andExpect(jsonPath("$.userId").value(userId));
        }
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
        when(findRoles.execute(ORG_KEY)).thenReturn(List.of(
                new CatalogRole("org-admin", null, true, RoleOrigin.PLATFORM),
                new CatalogRole("accounting", "Books", false, RoleOrigin.ADMIN)));

        var body = roles.listOrganizationRoles(ORG_KEY).getBody();

        assertThat(body).isNotNull().hasSize(2);
        assertThat(body.get(0).getOrigin()).isEqualTo(OrganizationRole.OriginEnum.PLATFORM);
        assertThat(body.get(1).getOrigin()).isEqualTo(OrganizationRole.OriginEnum.ADMIN);
    }

    @Test
    void definingARoleAnswers201WithTheRoleAsCreated() {
        when(createRole.execute(ORG_KEY, "accounting", "Books"))
                .thenReturn(new CatalogRole("accounting", "Books", false, RoleOrigin.ADMIN));

        var response = roles.createOrganizationRole(ORG_KEY, new NewOrganizationRole("accounting").description("Books"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("accounting");
        assertThat(response.getBody().getOrigin()).isEqualTo(OrganizationRole.OriginEnum.ADMIN);
    }

    @Test
    void changingARolesDescriptionAnswersTheRole() {
        when(updateRole.execute(ORG_KEY, "accounting", "New"))
                .thenReturn(new CatalogRole("accounting", "New", false, RoleOrigin.ADMIN));

        var response = roles.updateOrganizationRole(ORG_KEY, "accounting", new OrganizationRoleUpdate().description("New"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDescription()).isEqualTo("New");
    }

    @Test
    void deletingARoleAnswers204() {
        var response = roles.deleteOrganizationRole(ORG_KEY, "accounting");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteRole).execute(ORG_KEY, "accounting");
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

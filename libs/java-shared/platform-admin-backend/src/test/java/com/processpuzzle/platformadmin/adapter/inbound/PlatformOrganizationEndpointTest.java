package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.platformadmin.PlatformAdminTestFixtures;
import com.processpuzzle.platformadmin.model.AdminUserInput;
import com.processpuzzle.platformadmin.model.OrganizationUpdate;
import com.processpuzzle.platformadmin.usecase.ActivateOrganization;
import com.processpuzzle.platformadmin.usecase.AssignOrganizationAdmin;
import com.processpuzzle.platformadmin.usecase.DeleteOrganization;
import com.processpuzzle.platformadmin.usecase.FindAllOrganizations;
import com.processpuzzle.platformadmin.usecase.FindOrganization;
import com.processpuzzle.platformadmin.usecase.OrganizationDetails;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.platformadmin.usecase.SuspendOrganization;
import com.processpuzzle.platformadmin.usecase.UpdateOrganization;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

import java.util.List;

import static com.processpuzzle.platformadmin.PlatformAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("java:S5778")
class PlatformOrganizationEndpointTest {

    private FindAllOrganizations findAllOrganizations;
    private FindOrganization findOrganization;
    private UpdateOrganization updateOrganization;
    private DeleteOrganization deleteOrganization;
    private SuspendOrganization suspendOrganization;
    private ActivateOrganization activateOrganization;
    private AssignOrganizationAdmin assignOrganizationAdmin;

    @BeforeEach
    void setUp() {
        findAllOrganizations = mock(FindAllOrganizations.class);
        findOrganization = mock(FindOrganization.class);
        updateOrganization = mock(UpdateOrganization.class);
        deleteOrganization = mock(DeleteOrganization.class);
        suspendOrganization = mock(SuspendOrganization.class);
        activateOrganization = mock(ActivateOrganization.class);
        assignOrganizationAdmin = mock(AssignOrganizationAdmin.class);
    }

    @Test
    void listingAnswersThePageAndItsMetadata() {
        when(findAllOrganizations.execute(null, null, null, null))
                .thenReturn(new PageImpl<>(List.of(PlatformAdminTestFixtures.organization())));

        var response = endpoint(PlatformAdminTestFixtures.permissiveGuard())
                .listOrganizations(null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
    }

    /**
     * The staff read goes through {@code executeUnguarded}, because {@code execute} checks tenant
     * membership and staff have none. Asserting which method is called looks like testing an
     * implementation detail; it is not — the guarded one would 403 every staff request, and no check
     * at all would be an unauthenticated read of any tenant.
     */
    @Test
    void readingOneOrganizationSkipsTheMembershipCheckButNotTheStaffCheck() {
        when(findOrganization.executeUnguarded(ORG_KEY))
                .thenReturn(PlatformAdminTestFixtures.organization());

        var body = endpoint(PlatformAdminTestFixtures.permissiveGuard())
                .getOrganizationAsPlatformAdmin(ORG_KEY).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getKey()).isEqualTo(ORG_KEY);
        verify(findOrganization).executeUnguarded(ORG_KEY);

        assertThatThrownBy(() -> endpoint(PlatformAdminTestFixtures.denyingGuard())
                .getOrganizationAsPlatformAdmin(ORG_KEY))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void updatingForwardsTheMappedDetailsRatherThanTheDto() {
        OrganizationUpdate input = new OrganizationUpdate("Renamed");
        input.setDefaultLocale("de-DE");
        OrganizationDetails expected = new OrganizationDetails("Renamed", null, null, "de-DE");
        when(updateOrganization.executeAsPlatformAdmin(ORG_KEY, expected))
                .thenReturn(PlatformAdminTestFixtures.organization());

        assertThat(endpoint(PlatformAdminTestFixtures.permissiveGuard())
                .updateOrganizationAsPlatformAdmin(ORG_KEY, input).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        verify(updateOrganization).executeAsPlatformAdmin(ORG_KEY, expected);
    }

    @Test
    void deletingAnswers204WithNoBody() {
        var response = endpoint(PlatformAdminTestFixtures.permissiveGuard())
                .deleteOrganizationAsPlatformAdmin(ORG_KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(deleteOrganization).executeAsPlatformAdmin(ORG_KEY);
    }

    @Test
    void suspendAndActivateAnswerTheUpdatedOrganization() {
        when(suspendOrganization.execute(ORG_KEY)).thenReturn(PlatformAdminTestFixtures.organization());
        when(activateOrganization.execute(ORG_KEY)).thenReturn(PlatformAdminTestFixtures.organization());
        var endpoint = endpoint(PlatformAdminTestFixtures.permissiveGuard());

        assertThat(endpoint.suspendOrganization(ORG_KEY).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(endpoint.activateOrganization(ORG_KEY).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void assigningAnAdministratorAnswers201WithTheCreatedUser() {
        var user = new IdentityRealmPort.NewUser("ada", "ada@my-org.example", "Ada", "Lovelace");
        when(assignOrganizationAdmin.execute(any(), any())).thenReturn(
                new AssignOrganizationAdmin.Result("kc-1", ORG_KEY, user, List.of("org-admin")));

        var response = endpoint(PlatformAdminTestFixtures.permissiveGuard())
                .assignOrganizationAdmin(ORG_KEY, new AdminUserInput("ada", "ada@my-org.example"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo("kc-1");
        assertThat(response.getBody().getRealm()).isEqualTo(ORG_KEY);
    }

    /**
     * The controller holds no authorization of its own except on the one read that needed it, so a
     * denying guard must not stop it delegating — the use case is where the refusal comes from.
     */
    @Test
    void everyOtherOperationLeavesItsAuthorizationToTheUseCase() {
        when(findAllOrganizations.execute(null, null, null, null)).thenReturn(new PageImpl<>(List.of()));

        endpoint(PlatformAdminTestFixtures.denyingGuard()).listOrganizations(null, null, null, null);

        verify(findAllOrganizations).execute(null, null, null, null);
        verifyNoInteractions(findOrganization);
    }

    private PlatformOrganizationEndpoint endpoint(OrganizationGuard guard) {
        return new PlatformOrganizationEndpoint(findAllOrganizations, findOrganization,
                updateOrganization, deleteOrganization, suspendOrganization, activateOrganization,
                assignOrganizationAdmin, guard, new PlatformAdminMapper());
    }
}

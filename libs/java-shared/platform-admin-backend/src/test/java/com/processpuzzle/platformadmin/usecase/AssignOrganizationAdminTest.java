package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.platformadmin.PlatformAdminTestFixtures;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.usecase.exception.IdentityProviderUnavailableException;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationStatusConflictException;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.processpuzzle.platformadmin.PlatformAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("java:S5778")
class AssignOrganizationAdminTest {

    private static final IdentityRealmPort.NewUser USER =
            new IdentityRealmPort.NewUser("ada", "ada@my-org.example", "Ada", "Lovelace");

    private OrganizationRepository repository;
    private IdentityRealmPort realms;

    @BeforeEach
    void setUp() {
        repository = mock(OrganizationRepository.class);
        realms = mock(IdentityRealmPort.class);
        when(realms.createAdminUser(anyString(), any(), anyList())).thenReturn("kc-user-1");
    }

    /**
     * Both roles, not just {@code org-admin}. An administrator is also a member: role checks
     * elsewhere in the platform (nav visibility, workflow role assignment) match on
     * {@code org-member}, and an admin holding only the admin role would be invisible to all of them.
     */
    @Test
    void createsTheUserInTheTenantsOwnRealmWithBothRoles() {
        given(OrganizationStatus.ACTIVE);

        AssignOrganizationAdmin.Result result =
                assign(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY, USER);

        assertThat(result.userId()).isEqualTo("kc-user-1");
        assertThat(result.realm()).isEqualTo(ORG_KEY);
        assertThat(result.roles())
                .containsExactly(IdentityRealmPort.ORG_ADMIN_ROLE, IdentityRealmPort.ORG_MEMBER_ROLE);
        assertThat(result.user()).isEqualTo(USER);
    }

    /**
     * A PROVISIONING tenant has no realm yet. Without this check the call would fail deep inside
     * Keycloak with a message about a missing realm rather than about the tenant.
     */
    @Test
    void aProvisioningTenantIsRefusedBeforeTheIdentityProviderIsCalled() {
        given(OrganizationStatus.PROVISIONING);

        assertThatThrownBy(() -> assign(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY, USER))
                .isInstanceOf(OrganizationStatusConflictException.class);

        verifyNoInteractions(realms);
    }

    /** A suspended tenant still has a realm, and replacing a locked-out admin is exactly the case. */
    @Test
    void aSuspendedTenantStillAcceptsAnAdministrator() {
        given(OrganizationStatus.SUSPENDED);

        assertThat(assign(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY, USER).userId())
                .isEqualTo("kc-user-1");
    }

    @Test
    void unknownKey_is404AndNoUserIsCreated() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assign(PlatformAdminTestFixtures.permissiveGuard()).execute("nope", USER))
                .isInstanceOf(OrganizationNotFoundException.class);

        verifyNoInteractions(realms);
    }

    @Test
    void requiresStaffAuthority() {
        assertThatThrownBy(() -> assign(PlatformAdminTestFixtures.denyingGuard()).execute(ORG_KEY, USER))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(repository, realms);
    }

    /**
     * There is no local row to keep consistent — Keycloak is the system of record for users — so a
     * failure propagates rather than being compensated. The 503 it maps to tells the caller the user
     * was not created and the same request can be sent again.
     */
    @Test
    void anIdentityProviderFailurePropagates() {
        given(OrganizationStatus.ACTIVE);
        when(realms.createAdminUser(anyString(), any(), anyList()))
                .thenThrow(new IdentityProviderUnavailableException("down"));

        assertThatThrownBy(() -> assign(PlatformAdminTestFixtures.permissiveGuard()).execute(ORG_KEY, USER))
                .isInstanceOf(IdentityProviderUnavailableException.class);
    }

    private AssignOrganizationAdmin assign(OrganizationGuard guard) {
        return new AssignOrganizationAdmin(repository, guard, realms);
    }

    private void given(OrganizationStatus status) {
        when(repository.findById(ORG_KEY))
                .thenReturn(Optional.of(PlatformAdminTestFixtures.organization(status)));
    }
}

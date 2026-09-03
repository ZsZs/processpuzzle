package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.OrgAdminTestFixtures;
import com.processpuzzle.orgadmin.usecases.inbound.exception.OrganizationSuspendedException;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.usecase.FindOrganization;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import org.junit.jupiter.api.Test;

import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The gate every operation in this module passes through. Three refusals, and the order between the
 * first two is a security property rather than a preference — see the class Javadoc.
 */
class TenantRealmResolverTest {

    @Test
    void anActiveTenantResolvesToItsOwnKeyAsTheRealmName() {
        assertThat(OrgAdminTestFixtures.resolver().resolve(ORG_KEY)).isEqualTo(ORG_KEY);
    }

    /**
     * Design rights, not mere membership: deciding who may sign in is an administrative act, so an
     * ordinary member of a tenant must not be able to administer it.
     */
    @Test
    void aCallerWithoutDesignRightsIsRefusedBeforeTheTenantIsEvenRead() {
        FindOrganization findOrganization = mock(FindOrganization.class);
        TenantRealmResolver resolver =
                new TenantRealmResolver(findOrganization, OrgAdminTestFixtures.denyingGuard());

        assertThatThrownBy(() -> resolver.resolve(ORG_KEY))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        // The ordering is the point: reading first would let a non-member tell an existing orgKey
        // from a non-existent one by whether the answer was 404 or 403.
        verifyNoInteractions(findOrganization);
    }

    @Test
    void anUnknownTenantIs404() {
        assertThatThrownBy(() -> OrgAdminTestFixtures.resolverForUnknownTenant().resolve(ORG_KEY))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    /** Its realm is disabled, so administering it would fail deep inside Keycloak instead. */
    @Test
    void aSuspendedTenantIsRefused() {
        assertThatThrownBy(() -> OrgAdminTestFixtures
                .resolverFor(OrganizationStatus.SUSPENDED).resolve(ORG_KEY))
                .isInstanceOf(OrganizationSuspendedException.class)
                .hasMessageContaining(ORG_KEY);
    }

    /** Its realm does not exist at all yet — a stronger reason than suspension. */
    @Test
    void aProvisioningTenantIsRefusedToo() {
        assertThatThrownBy(() -> OrgAdminTestFixtures
                .resolverFor(OrganizationStatus.PROVISIONING).resolve(ORG_KEY))
                .isInstanceOf(OrganizationSuspendedException.class);
    }

    /**
     * The realm name comes back from the organization rather than from the path parameter, so a
     * caller cannot keep using the raw segment after validating it.
     */
    @Test
    void theRealmComesFromTheOrganizationNotFromThePathParameter() {
        FindOrganization findOrganization = mock(FindOrganization.class);
        org.mockito.Mockito.when(findOrganization.executeUnguarded(anyString()))
                .thenReturn(new com.processpuzzle.platformadmin.domain.Organization(
                        "canonical-key", "Name", null, null, null, OrganizationStatus.ACTIVE));
        TenantRealmResolver resolver =
                new TenantRealmResolver(findOrganization, OrgAdminTestFixtures.permissiveGuard());

        assertThat(resolver.resolve("Canonical-Key")).isEqualTo("canonical-key");
    }
}

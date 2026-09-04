package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.orgadmin.OrgAdminTestFixtures;
import com.processpuzzle.orgadmin.usecases.inbound.exception.OrganizationSuspendedException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownOrganizationException;
import com.processpuzzle.orgadmin.usecases.outbound.TenantRealmDirectory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The gate every operation in this module passes through. Three refusals, and the order between the
 * first two is a security property rather than a preference — see the class Javadoc.
 */
class TenantRealmResolverTest {

    @Test
    void anAdministerableTenantResolvesToItsOwnKeyAsTheRealmName() {
        assertThat(OrgAdminTestFixtures.resolver().resolve(ORG_KEY)).isEqualTo(ORG_KEY);
    }

    /**
     * Design rights, not mere membership: deciding who may sign in is an administrative act, so an
     * ordinary member of a tenant must not be able to administer it.
     */
    @Test
    void aCallerWithoutDesignRightsIsRefusedBeforeTheTenantIsEvenRead() {
        boolean[] directoryTouched = {false};
        TenantRealmResolver resolver = OrgAdminTestFixtures.resolverOver(orgKey -> {
            directoryTouched[0] = true;
            return Optional.of(new TenantRealmDirectory.Tenant(orgKey, true));
        }, OrgAdminTestFixtures.denyingGuard());

        assertThatThrownBy(() -> resolver.resolve(ORG_KEY))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        // The ordering is the point: reading first would let a non-member tell an existing orgKey
        // from a non-existent one by whether the answer was 404 or 403.
        assertThat(directoryTouched[0]).isFalse();
    }

    @Test
    void anUnknownTenantIs404() {
        assertThatThrownBy(() -> OrgAdminTestFixtures.resolverForUnknownTenant().resolve(ORG_KEY))
                .isInstanceOf(UnknownOrganizationException.class);
    }

    /**
     * Its realm is disabled or does not exist yet, so administering it would fail deep inside
     * Keycloak instead.
     *
     * <p>This was two tests, one for {@code SUSPENDED} and one for {@code PROVISIONING}. They are one
     * now, because the port hands this module a single {@code administerable} bit rather than a
     * lifecycle state — the two states are refused identically here, and telling them apart is the
     * adapter's job in whichever application owns a tenant registry. That mapping is consequently no
     * longer covered by any test in this repository; it will be covered where the adapter lives.
     */
    @Test
    void aTenantThatIsNotAdministerableIsRefused() {
        assertThatThrownBy(() -> OrgAdminTestFixtures.resolverFor(false).resolve(ORG_KEY))
                .isInstanceOf(OrganizationSuspendedException.class)
                .hasMessageContaining(ORG_KEY);
    }

    /**
     * The realm name comes back from the directory rather than from the path parameter, so a caller
     * cannot keep using the raw segment after validating it. A registry that canonicalises a
     * differently-cased key is the case that makes the difference visible.
     */
    @Test
    void theRealmComesFromTheDirectoryNotFromThePathParameter() {
        TenantRealmResolver resolver = OrgAdminTestFixtures.resolverOver(
                orgKey -> Optional.of(new TenantRealmDirectory.Tenant("canonical-key", true)),
                OrgAdminTestFixtures.permissiveGuard());

        assertThat(resolver.resolve("Canonical-Key")).isEqualTo("canonical-key");
    }

    /**
     * The default that every deployment in this repository actually runs with. It is not a stub for a
     * missing adapter: where realm name and organization key are the same string — the platform's own
     * naming rule — it is the correct answer, and org-admin is fully usable without any tenant
     * registry behind it.
     */
    @Test
    void withNoAdapterWiredTheConventionAnswersEveryKey() {
        TenantRealmResolver resolver = new TenantRealmResolver(
                OrgAdminTestFixtures.emptyProvider(), OrgAdminTestFixtures.permissiveGuard());

        assertThat(resolver.resolve("any-tenant")).isEqualTo("any-tenant");
    }
}

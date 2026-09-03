package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.platformadmin.PlatformAdminTestFixtures;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationAccessDeniedException;
import com.processpuzzle.platformadmin.usecase.port.OrganizationAccessPolicy;
import com.processpuzzle.platformadmin.usecase.port.PermitAllOrganizationAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The guard is the single place any feature consults {@link OrganizationAccessPolicy}, so what is
 * tested here is delegation and the fallback — not policy logic, of which the guard has none beyond
 * the "no roles means any member" default.
 *
 * <p>The nav-filtering half of this class stayed in base-app as {@code NavVisibilityFilterTest}: it
 * walks base-app's {@code Region}/{@code NavNode}, which this module cannot see.
 */
class OrganizationGuardTest {

    @Test
    void anEntryWithoutRoles_isVisibleToAnyMember() {
        OrganizationGuard guard = withRoles(false);

        assertThat(guard.isVisible(null)).isTrue();
        assertThat(guard.isVisible(List.of())).isTrue();
    }

    @Test
    void anEntryWithRoles_isVisibleOnlyWhenThePolicyAgrees() {
        assertThat(withRoles(true).isVisible(List.of("CLAIMS_AUDITOR"))).isTrue();
        assertThat(withRoles(false).isVisible(List.of("CLAIMS_AUDITOR"))).isFalse();
    }

    @Test
    void accessAndDesignChecksAreDelegatedToThePolicy() {
        assertThatCode(() -> {
            OrganizationGuard permitted = PlatformAdminTestFixtures.permissiveGuard();
            permitted.requireAccess("my-org");
            permitted.requireDesign("my-org");
        }).doesNotThrowAnyException();

        OrganizationGuard denied = PlatformAdminTestFixtures.denyingGuard();
        assertThatThrownBy(() -> denied.requireAccess("my-org"))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> denied.requireDesign("my-org"))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    /**
     * The staff check takes no {@code orgKey} because it cannot: a platform administrator acts across
     * all tenants, so there is nothing to compare the principal's own organization against.
     */
    @Test
    void theStaffCheckIsDelegatedToThePolicyToo() {
        assertThatCode(() -> PlatformAdminTestFixtures.permissiveGuard().requirePlatformAdmin())
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> PlatformAdminTestFixtures.denyingGuard().requirePlatformAdmin())
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    /**
     * {@code getIfUnique} rather than {@code @ConditionalOnMissingBean}: with no application policy
     * bean the guard has to fall back to permit-all, order-independently.
     */
    @Test
    @SuppressWarnings("unchecked")
    void withNoPolicyBean_theGuardFallsBackToPermitAll() {
        ObjectProvider<OrganizationAccessPolicy> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenAnswer(call ->
                ((java.util.function.Supplier<OrganizationAccessPolicy>) call.getArgument(0)).get());

        OrganizationGuard guard = new OrganizationGuard(provider);

        assertThatCode(guard::requirePlatformAdmin).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireDesign("my-org")).doesNotThrowAnyException();
        assertThat(guard.isVisible(List.of("ANY_ROLE"))).isTrue();
        assertThat(new PermitAllOrganizationAccessPolicy().hasAnyRole(List.of("ANY_ROLE"))).isTrue();
    }

    private static OrganizationGuard withRoles(boolean granted) {
        return PlatformAdminTestFixtures.guardWith(new OrganizationAccessPolicy() {
            @Override
            public boolean hasAnyRole(Collection<String> requiredRoles) {
                return granted;
            }
        });
    }
}

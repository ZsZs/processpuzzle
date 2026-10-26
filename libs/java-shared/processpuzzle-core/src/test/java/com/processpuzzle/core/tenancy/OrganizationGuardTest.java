package com.processpuzzle.core.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The guard is the single place any feature consults {@link OrganizationAccessPolicy}, so what is
 * tested here is delegation and explicit policy wiring — not host policy logic beyond
 * the "no roles means any member" default.
 *
 * <p>The nav-filtering half of this class stayed in base-app as {@code NavVisibilityFilterTest}: it
 * walks base-app's {@code Region}/{@code NavNode}, which this module cannot see.
 *
 * <p>The three guard factories below were {@code PlatformAdminTestFixtures}' before the guard moved
 * to core. They are inlined rather than published from a shared test artifact because core must not
 * depend on a feature library's test tree — the feature libraries keep their own copies, as they
 * already did of each other's.
 */
@SuppressWarnings("java:S5778")
class OrganizationGuardTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withBean(OrganizationGuard.class);

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
            OrganizationGuard permitted = permissiveGuard();
            permitted.requireAccess("my-org");
            permitted.requireDesign("my-org");
        }).doesNotThrowAnyException();

        OrganizationGuard denied = denyingGuard();
        assertThatThrownBy(() -> denied.requireAccess("my-org"))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> denied.requireDesign("my-org"))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    /**
     * Administration is delegated like the other checks, and a policy written before the method existed
     * does not grant it: {@code denyingGuard} overrides access and design only, so its refusal here is
     * the interface's default.
     */
    @Test
    void administrationIsDelegatedAndDeniedByDefault() {
        assertThatCode(() -> permissiveGuard().requireAdministration("my-org")).doesNotThrowAnyException();

        assertThatThrownBy(() -> denyingGuard().requireAdministration("my-org"))
                .isInstanceOf(OrganizationAccessDeniedException.class)
                .hasMessageContaining("my-org");
    }

    /**
     * The staff check takes no {@code orgKey} because it cannot: a platform administrator acts across
     * all tenants, so there is nothing to compare the principal's own organization against.
     */
    @Test
    void theStaffCheckIsDelegatedToThePolicyToo() {
        assertThatCode(() -> permissiveGuard().requirePlatformAdmin())
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> denyingGuard().requirePlatformAdmin())
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void withNoPolicyBean_startupFails() {
        contextRunner.run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class));
    }

    @Test
    void aSinglePolicyBean_isUsed() {
        contextRunner.withBean("restrictive", OrganizationAccessPolicy.class, () -> new OrganizationAccessPolicy() {})
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertDenied(context.getBean(OrganizationGuard.class));
                });
    }

    @Test
    void ambiguousPolicies_failStartupRatherThanPermitAccess() {
        contextRunner.withBean("first", OrganizationAccessPolicy.class, () -> new OrganizationAccessPolicy() {})
                .withBean("second", OrganizationAccessPolicy.class, PermitAllOrganizationAccessPolicy::new)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    @Test
    void aPrimaryPolicy_resolvesMultipleCandidates() {
        contextRunner.withBean("demo", OrganizationAccessPolicy.class, PermitAllOrganizationAccessPolicy::new)
                .withBean("restrictive", OrganizationAccessPolicy.class, () -> new OrganizationAccessPolicy() {},
                        definition -> definition.setPrimary(true))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertDenied(context.getBean(OrganizationGuard.class));
                });
    }

    @Test
    void explicitDemonstrationPolicy_permitsAllChecks() {
        contextRunner.withBean(OrganizationAccessPolicy.class, PermitAllOrganizationAccessPolicy::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    OrganizationGuard guard = context.getBean(OrganizationGuard.class);
                    assertThatCode(() -> guard.requireAccess("my-org")).doesNotThrowAnyException();
                    assertThatCode(() -> guard.requireDesign("my-org")).doesNotThrowAnyException();
                    assertThatCode(guard::requirePlatformAdmin).doesNotThrowAnyException();
                    assertThat(guard.isVisible(List.of("ANY_ROLE"))).isTrue();
                });
    }

    @Test
    void partialPolicies_denyUnimplementedChecks() {
        OrganizationAccessPolicy policy = new OrganizationAccessPolicy() {
            @Override
            public void requireAccess(String orgKey) {
                // This host grants membership only.
            }
        };
        OrganizationGuard guard = guardWith(policy);
        assertThatCode(() -> guard.requireAccess("my-org")).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireDesign("my-org"))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(guard::requirePlatformAdmin)
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThat(guard.isVisible(List.of("ANY_ROLE"))).isFalse();
        assertThat(policy.hasAnyRole(null)).isFalse();
        assertThat(policy.hasAnyRole(List.of())).isFalse();
    }

    private static void assertDenied(OrganizationGuard guard) {
        assertThatThrownBy(() -> guard.requireAccess("my-org"))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> guard.requireDesign("my-org"))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(guard::requirePlatformAdmin)
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThat(guard.isVisible(List.of("ANY_ROLE"))).isFalse();
    }

    private static OrganizationGuard withRoles(boolean granted) {
        return guardWith(new OrganizationAccessPolicy() {
            @Override
            public boolean hasAnyRole(Collection<String> requiredRoles) {
                return granted;
            }
        });
    }

    private static OrganizationGuard permissiveGuard() {
        return guardWith(new PermitAllOrganizationAccessPolicy());
    }

    private static OrganizationGuard denyingGuard() {
        return guardWith(new OrganizationAccessPolicy() {
            @Override
            public void requireAccess(String orgKey) {
                throw new OrganizationAccessDeniedException(orgKey);
            }

            @Override
            public void requireDesign(String orgKey) {
                throw new OrganizationAccessDeniedException(orgKey);
            }

            @Override
            public void requirePlatformAdmin() {
                throw new OrganizationAccessDeniedException("platform");
            }
        });
    }

    @Test
    void declaringRolesPublishesThemAndSkipsAnEmptyList() {
        java.util.List<Object> events = new java.util.ArrayList<>();
        OrganizationGuard guard = new OrganizationGuard(providerOf(permitting()), events::add);

        guard.declareRoles("demo", "Referenced by x", java.util.List.of("auditor"));
        guard.declareRoles("demo", "Referenced by x", java.util.List.of());
        guard.declareRoles("demo", "Referenced by x", null);

        org.assertj.core.api.Assertions.assertThat(events)
                .containsExactly(new RolesReferenced("demo", "Referenced by x", java.util.List.of("auditor")));
    }

    private static OrganizationAccessPolicy permitting() {
        return new PermitAllOrganizationAccessPolicy();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<OrganizationAccessPolicy> providerOf(OrganizationAccessPolicy policy) {
        ObjectProvider<OrganizationAccessPolicy> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(policy);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static OrganizationGuard guardWith(OrganizationAccessPolicy policy) {
        ObjectProvider<OrganizationAccessPolicy> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(policy);
        return new OrganizationGuard(provider);
    }
}

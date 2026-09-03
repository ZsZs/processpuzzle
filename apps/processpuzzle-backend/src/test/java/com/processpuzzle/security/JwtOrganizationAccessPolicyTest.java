package com.processpuzzle.security;

import com.processpuzzle.platformadmin.usecase.exception.OrganizationAccessDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The check the contracts have declared all along and nothing enforced: an {@code orgKey} in the path
 * is not an authorization decision, so it is compared against the realm that issued the token.
 */
class JwtOrganizationAccessPolicyTest {

    private final JwtOrganizationAccessPolicy policy =
            new JwtOrganizationAccessPolicy(new CurrentPrincipal(SecurityTestTokens.properties()));

    @AfterEach
    void clearContext() {
        SecurityTestTokens.clear();
    }

    @Test
    void aMemberMayActOnItsOwnOrganization() {
        SecurityTestTokens.authenticateAs("my-org", "ada", "org-member");

        assertThatCode(() -> {
            policy.requireAccess("my-org");
            policy.requireDesign("my-org");
        }).doesNotThrowAnyException();
    }

    /** The whole point of the phase: editing the URL is no longer enough to reach another tenant. */
    @Test
    void aTokenFromOneOrganizationIsRefusedOnAnother() {
        SecurityTestTokens.authenticateAs("org-a", "ada", "org-admin");

        assertThatThrownBy(() -> policy.requireAccess("org-b"))
                .isInstanceOf(OrganizationAccessDeniedException.class)
                .hasMessageContaining("org-b");
        assertThatThrownBy(() -> policy.requireDesign("org-b"))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void platformStaffMayActOnAnyOrganization() {
        SecurityTestTokens.authenticateAsPlatformStaff();

        assertThatCode(() -> {
            policy.requireAccess("org-a");
            policy.requireDesign("org-b");
            policy.requirePlatformAdmin();
        }).doesNotThrowAnyException();
    }

    @Test
    void aTenantTokenIsNotPlatformStaffHoweverPrivilegedItIsInItsOwnRealm() {
        SecurityTestTokens.authenticateAs("my-org", "ada", "org-admin", "org-member");

        assertThatThrownBy(policy::requirePlatformAdmin)
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    /**
     * The compromise that lets the resource server ship: the Angular applications send no token yet,
     * so denying here would answer 403 to every existing screen. Documented on the class, and
     * asserted so that changing it is a deliberate act rather than a side effect.
     */
    @Test
    void anUnauthenticatedRequestIsPermitted_thePhaseThreeCompromise() {
        assertThatCode(() -> {
            policy.requireAccess("my-org");
            policy.requireDesign("my-org");
        }).doesNotThrowAnyException();
    }

    @Test
    void anUnauthenticatedRequestIsStillNotPlatformStaff() {
        assertThatThrownBy(policy::requirePlatformAdmin)
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void anEntryWithoutRolesIsVisibleToAnyone() {
        SecurityTestTokens.authenticateAs("my-org", "ada");

        assertThat(policy.hasAnyRole(null)).isTrue();
        assertThat(policy.hasAnyRole(List.of())).isTrue();
    }

    @Test
    void anEntryWithRolesNeedsOneOfThemFromTheToken() {
        SecurityTestTokens.authenticateAs("my-org", "ada", "claims-auditor");

        assertThat(policy.hasAnyRole(List.of("claims-auditor"))).isTrue();
        assertThat(policy.hasAnyRole(List.of("claims-approver", "claims-auditor"))).isTrue();
        assertThat(policy.hasAnyRole(List.of("claims-approver"))).isFalse();
    }

    @Test
    void platformStaffSeeEveryRestrictedEntry() {
        SecurityTestTokens.authenticateAsPlatformStaff();

        assertThat(policy.hasAnyRole(List.of("claims-auditor"))).isTrue();
    }

    /**
     * Consistent with {@link #anUnauthenticatedRequestIsPermitted_thePhaseThreeCompromise}: filtering
     * nav entries away for a caller the policy has just permitted would hide entries in exactly the
     * apps someone had bothered to configure roles for.
     */
    @Test
    void anUnauthenticatedRequestSeesEveryRestrictedEntryToo() {
        assertThat(policy.hasAnyRole(List.of("claims-auditor"))).isTrue();
    }
}

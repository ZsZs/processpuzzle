package com.processpuzzle.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RealmRoleMembershipPolicyTest {

    private final RealmRoleMembershipPolicy policy =
            new RealmRoleMembershipPolicy(new CurrentPrincipal(SecurityTestTokens.properties()));

    @AfterEach
    void clearContext() {
        SecurityTestTokens.clear();
    }

    @Test
    void emptyEntityRoleIsAlwaysAllowed() {
        assertThat(policy.isMember("my-org", "ada", null)).isTrue();
        assertThat(policy.isMember("my-org", "ada", "   ")).isTrue();
    }

    @Test
    void unauthenticatedRequestIsAllowed() {
        assertThat(policy.isMember("my-org", "ada", "org-member")).isTrue();
    }

    @Test
    void authenticatedUserOutsideOrganizationIsAllowed() {
        SecurityTestTokens.authenticateAs("other-org", "ada", "org-member");

        assertThat(policy.isMember("my-org", "ada", "org-member")).isTrue();
    }

    @Test
    void thirdPartyMembershipCheckIsAllowed() {
        SecurityTestTokens.authenticateAs("my-org", "ada", "org-member");

        assertThat(policy.isMember("my-org", "grace", "org-member")).isTrue();
        assertThat(policy.isMember("my-org", null, "org-member")).isTrue();
    }

    @Test
    void currentPrincipalNeedsTheRequestedRole() {
        SecurityTestTokens.authenticateAs("my-org", "ada", "org-member");

        assertThat(policy.isMember("my-org", "ada", "org-member")).isTrue();
        assertThat(policy.isMember("my-org", "ada", "org-admin")).isFalse();
    }
}

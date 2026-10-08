package com.processpuzzle.security;

import com.processpuzzle.core.security.CurrentPrincipal;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RealmStartAuthorizationPolicyTest {

    private final RealmStartAuthorizationPolicy policy =
            new RealmStartAuthorizationPolicy(new CurrentPrincipal(SecurityTestTokens.properties()));

    @AfterEach
    void clearContext() {
        SecurityTestTokens.clear();
    }

    @Test
    void unauthenticatedRequestIsAllowed() {
        assertThat(policy.currentPrincipalHoldsAny("my-org", Set.of("org-admin"))).isTrue();
    }

    @Test
    void authenticatedUserOutsideOrganizationIsAllowed() {
        SecurityTestTokens.authenticateAs("other-org", "ada", "org-member");

        assertThat(policy.currentPrincipalHoldsAny("my-org", Set.of("org-admin"))).isTrue();
    }

    @Test
    void memberNeedsOneOfTheRoles() {
        SecurityTestTokens.authenticateAs("my-org", "ada", "org-member");

        assertThat(policy.currentPrincipalHoldsAny("my-org", Set.of("org-admin", "org-member"))).isTrue();
        assertThat(policy.currentPrincipalHoldsAny("my-org", Set.of("org-admin"))).isFalse();
    }
}

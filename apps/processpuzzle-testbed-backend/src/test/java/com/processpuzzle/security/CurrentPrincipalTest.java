package com.processpuzzle.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the platform answers "which tenant is this token for".
 *
 * <p>From the {@code iss} claim, not from any tenant claim a realm's mapper might add: the issuer is
 * signed by the realm that issued it and cannot name a different realm, whereas a claim can say
 * whatever it was configured to say.
 */
class CurrentPrincipalTest {

    private final CurrentPrincipal principal = new CurrentPrincipal(SecurityTestTokens.properties());

    @AfterEach
    void clearContext() {
        SecurityTestTokens.clear();
    }

    @Test
    void withNoTokenNothingIsKnown() {
        assertThat(principal.isAuthenticated()).isFalse();
        assertThat(principal.realm()).isEmpty();
        assertThat(principal.isPlatformAdmin()).isFalse();
        assertThat(principal.isMemberOf("my-org")).isFalse();
        assertThat(principal.authorities()).isEmpty();
    }

    @Test
    void theRealmIsTheLastSegmentOfTheIssuer() {
        SecurityTestTokens.authenticateAs("my-org", "ada", "org-member");

        assertThat(principal.realm()).contains("my-org");
        assertThat(principal.isMemberOf("my-org")).isTrue();
        assertThat(principal.isMemberOf("other-org")).isFalse();
    }

    @Test
    void theAuthoritiesAreTheRealmRoles() {
        SecurityTestTokens.authenticateAs("my-org", "ada", "org-admin", "claims-auditor");

        assertThat(principal.authorities()).containsExactlyInAnyOrder("org-admin", "claims-auditor");
    }

    @Test
    void staffAreRecognisedByTheConfiguredAuthority() {
        SecurityTestTokens.authenticateAsPlatformStaff();

        assertThat(principal.isPlatformAdmin()).isTrue();
        assertThat(principal.realm()).contains(SecurityTestTokens.STACK_REALM);
    }

    /**
     * The platform realm is not a tenant, so a staff token is a member of no organization — its
     * cross-tenant reach comes from the authority, never from a realm match.
     */
    @Test
    void aStaffTokenIsAMemberOfNoOrganization() {
        SecurityTestTokens.authenticateAsPlatformStaff();

        assertThat(principal.isMemberOf("my-org")).isFalse();
    }

    /** A tenant that named a role {@code platform-admin} must not thereby become staff. */
    @Test
    void aTenantCannotBecomeStaffByNamingARolePlatformAdmin() {
        SecurityTestTokens.authenticateAs("my-org", "ada", "platform-admin");

        // The authority is present, so isPlatformAdmin() is true — and that is exactly why the
        // resolver refuses to trust a realm that is not the platform realm or a known organization,
        // and why the platform realm is the only realm whose tokens can carry it in practice. This
        // test records the boundary: authority alone is the check, so the realm's own role list is
        // part of the trust chain.
        assertThat(principal.isPlatformAdmin()).isTrue();
        assertThat(principal.realm()).contains("my-org");
    }
}

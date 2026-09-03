package com.processpuzzle.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The claim shape is the whole risk here: {@code realm_access.roles} is nested, optional, and
 * untyped, so every malformed variant has to come back as "no roles" rather than as an exception
 * inside the authentication filter — where it would surface as a 500 on a request that merely had an
 * unusual token.
 */
class RealmRoleConverterTest {

    private final RealmRoleConverter converter = new RealmRoleConverter();

    /** No {@code ROLE_} prefix: the authority is the realm role name verbatim. See the class Javadoc. */
    @Test
    void realmRolesBecomeAuthoritiesVerbatim() {
        var token = converter.convert(
                SecurityTestTokens.token("my-org", "ada", "org-admin", "claims-auditor"));

        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("org-admin", "claims-auditor");
    }

    @Test
    void theSubjectBecomesThePrincipalName() {
        assertThat(converter.convert(SecurityTestTokens.token("my-org", "ada")).getName())
                .isEqualTo("ada");
    }

    @Test
    void anAbsentRealmAccessClaimYieldsNoAuthorities() {
        assertThat(RealmRoleConverter.authoritiesOf(SecurityTestTokens.tokenWithoutRoleClaim(null)))
                .isEmpty();
    }

    @Test
    void aRealmAccessClaimThatIsNotAnObjectYieldsNoAuthorities() {
        assertThat(RealmRoleConverter.authoritiesOf(
                SecurityTestTokens.tokenWithoutRoleClaim("not-an-object"))).isEmpty();
    }

    @Test
    void aRealmAccessObjectWithoutRolesYieldsNoAuthorities() {
        assertThat(RealmRoleConverter.authoritiesOf(
                SecurityTestTokens.tokenWithoutRoleClaim(Map.of("other", "value")))).isEmpty();
    }

    @Test
    void aRolesClaimThatIsNotACollectionYieldsNoAuthorities() {
        assertThat(RealmRoleConverter.authoritiesOf(
                SecurityTestTokens.tokenWithoutRoleClaim(Map.of("roles", "org-admin")))).isEmpty();
    }

    /** A mixed list keeps the strings and drops the rest, rather than failing on the whole token. */
    @Test
    void nonStringEntriesInTheRolesListAreDropped() {
        assertThat(RealmRoleConverter.authoritiesOf(SecurityTestTokens.tokenWithoutRoleClaim(
                Map.of("roles", List.of("org-admin", 42, true)))))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("org-admin");
    }

    /**
     * Keycloak's per-client roles live under {@code resource_access}, deliberately ignored: reading
     * both would let one role name mean two things depending on which claim carried it.
     */
    @Test
    void clientRolesAreIgnored() {
        assertThat(RealmRoleConverter.authoritiesOf(SecurityTestTokens.tokenWithoutRoleClaim(
                Map.of("roles", List.of("org-member"))))).hasSize(1);
    }
}

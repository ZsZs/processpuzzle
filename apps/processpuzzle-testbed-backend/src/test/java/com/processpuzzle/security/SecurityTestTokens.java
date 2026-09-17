package com.processpuzzle.security;

import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.security.RealmRoleConverter;
import com.processpuzzle.core.security.SecurityProperties;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Builds the tokens the two policy tests in this package reason about, and puts them in the security
 * context.
 *
 * <p>Real {@link Jwt} instances rather than mocks, because the thing under test is how a claim is
 * read: the realm is derived from {@code iss} and the authorities from {@code realm_access.roles},
 * and a mocked token would let a test pass while the claim names were wrong.
 *
 * <p><b>A deliberate duplicate of {@code com.processpuzzle.core.security.SecurityTestTokens}</b>, and
 * the smaller of two evils. The mechanism this helper drives moved to {@code processpuzzle-core}; the
 * two policy implementations it is used by did not, for the reasons that package's {@code
 * package-info} records. Sharing one helper across both would mean publishing a {@code test-jar} from
 * {@code processpuzzle-core} — a new artifact on Maven Central, in the release pipeline, forever —
 * so that two tests can call four methods. Duplicating test fixtures is cheap and visible; enlarging
 * a published artifact's surface is neither.
 *
 * <p>It drifting from core's copy costs nothing: these fixtures exist to exercise
 * {@link com.processpuzzle.security.JwtOrganizationAccessPolicy} and
 * {@link com.processpuzzle.security.RealmRoleMembershipPolicy}, and both read the token only through
 * {@link CurrentPrincipal}, which core's own tests cover directly.
 */
public final class SecurityTestTokens {

    public static final String ISSUER_BASE = "http://localhost:8180";
    /** The realm this deployment serves. These fixtures model the ADMIN stack. */
    public static final String STACK_REALM = "processpuzzle-admin";

    private SecurityTestTokens() {
    }

    /** A tenant member's token, from the realm named after the organization. */
    public static void authenticateAs(String realm, String subject, String... realmRoles) {
        Jwt token = token(realm, subject, realmRoles);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token, RealmRoleConverter.authoritiesOf(token), subject));
    }

    /** A ProcessPuzzle staff token: the admin stack realm, carrying {@code platform-admin}. */
    public static void authenticateAsPlatformStaff() {
        authenticateAs(STACK_REALM, "staff-1", "platform-admin");
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    public static Jwt token(String realm, String subject, String... realmRoles) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER_BASE + "/realms/" + realm)
                .subject(subject)
                .claim("realm_access", Map.of("roles", List.of(realmRoles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    public static SecurityProperties properties() {
        SecurityProperties properties = new SecurityProperties();
        properties.setIssuerBaseUrl(ISSUER_BASE);
        properties.setStackRealm(STACK_REALM);
        return properties;
    }
}

package com.processpuzzle.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Builds the tokens the policy tests reason about, and puts them in the security context.
 *
 * <p>Real {@link Jwt} instances rather than mocks, because the thing under test is how a claim is
 * read: the realm is derived from {@code iss} and the authorities from {@code realm_access.roles},
 * and a mocked token would let a test pass while the claim names were wrong.
 */
public final class SecurityTestTokens {

    public static final String ISSUER_BASE = "http://localhost:8180";
    /** The realm this deployment serves. These fixtures model the ADMIN stack. */
    public static final String STACK_REALM = "processpuzzle-admin";

    private SecurityTestTokens() {
    }

    /** A tenant member's token, from the realm named after the organization. */
    public static void authenticateAs(String realm, String subject, String... realmRoles) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token(realm, subject, realmRoles),
                        RealmRoleConverter.authoritiesOf(token(realm, subject, realmRoles)), subject));
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

    /** A token whose {@code realm_access} claim is missing or the wrong shape. */
    public static Jwt tokenWithoutRoleClaim(Object realmAccess) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER_BASE + "/realms/my-org")
                .subject("ada")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (realmAccess != null) {
            builder.claim("realm_access", realmAccess);
        } else {
            // A JWT always has at least one claim; use one that is not realm_access.
            builder.claim("scope", "openid");
        }
        return builder.build();
    }

    public static SecurityProperties properties() {
        SecurityProperties properties = new SecurityProperties();
        properties.setIssuerBaseUrl(ISSUER_BASE);
        properties.setStackRealm(STACK_REALM);
        return properties;
    }
}

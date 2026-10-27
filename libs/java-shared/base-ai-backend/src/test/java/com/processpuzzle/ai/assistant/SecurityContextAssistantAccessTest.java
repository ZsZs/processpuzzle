package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.security.SecurityProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityContextAssistantAccessTest {

    private final SecurityContextAssistantAccess access = new SecurityContextAssistantAccess(
        new CurrentPrincipal(new SecurityProperties()));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aDesignerOfTheTokensOrganizationMayDesignThere() {
        signIn("acme", "definition-designer");

        assertThat(access.currentOrganization()).isEqualTo("acme");
        assertThat(access.mayDesign()).isTrue();
    }

    @Test
    void aMemberWithoutTheDesignerRoleMayNotDesign() {
        signIn("acme", "reviewer");

        assertThat(access.currentOrganization()).isEqualTo("acme");
        assertThat(access.mayDesign()).isFalse();
    }

    /** In a shared realm the token names its organization under the alias the URL carries, not the realm. */
    @Test
    void aDesignerOfTheOrganizationNamedInASharedRealmMayDesignThere() {
        signInWithOrganizations("processpuzzle-custom", Map.of("zszs", Map.of("groups", List.of())), "definition-designer");

        assertThat(access.currentOrganization()).isEqualTo("zszs");
        assertThat(access.mayDesign()).isTrue();
    }

    /** Several organizations make no single one to act for, so the caller acts for none, as the group roles do. */
    @Test
    void aTokenNamingSeveralOrganizationsActsForNone() {
        signInWithOrganizations("processpuzzle-custom",
            Map.of("zszs", Map.of("groups", List.of()), "acme", Map.of("groups", List.of())), "definition-designer");

        assertThat(access.currentOrganization()).isEmpty();
        assertThat(access.mayDesign()).isFalse();
    }

    @Test
    void noTokenMeansNoOrganizationAndNoDesign() {
        assertThat(access.currentOrganization()).isEmpty();
        assertThat(access.mayDesign()).isFalse();
    }

    @Test
    void thePrincipalIsTheTokensSubjectWhenItNamesNoPerson() {
        signIn("acme", "definition-designer");

        assertThat(access.principal()).isEqualTo("user-1");
    }

    @Test
    void theAnonymousCallerHasNoPrincipal() {
        assertThat(access.principal()).isEmpty();
    }

    /** Puts a Keycloak-shaped token for {@code realm} with the given realm roles into the security context. */
    private static void signIn(String realm, String... roles) {
        signInWithOrganizations(realm, Map.of(), roles);
    }

    /** As {@link #signIn}, with the token's {@code organization} claim as given. */
    private static void signInWithOrganizations(String realm, Map<String, Object> organizations, String... roles) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token")
            .header("alg", "none")
            .issuer("https://auth.example.com/realms/" + realm)
            .subject("user-1")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300));
        if (!organizations.isEmpty()) {
            builder.claim("organization", organizations);
        }
        Jwt jwt = builder.build();
        List<GrantedAuthority> authorities = List.of(roles).stream()
            .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
            .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}

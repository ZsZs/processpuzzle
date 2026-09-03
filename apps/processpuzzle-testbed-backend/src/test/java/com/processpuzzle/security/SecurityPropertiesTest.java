package com.processpuzzle.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one piece of behaviour in {@link SecurityProperties}: {@code jwks-base-url} falls back to
 * {@code issuer-base-url}.
 *
 * <p>That fallback is what keeps a single-origin deployment — the prod topology, where nginx fronts
 * both the browser and this container under {@code /auth} — from needing a second URL it would only
 * ever set to the same value. Losing it would not fail loudly: it would make the JWKS URL
 * {@code null/realms/...}, i.e. a 401 on every token, in exactly the deployment that enforces
 * authentication.
 */
class SecurityPropertiesTest {

    @Test
    void jwksBaseUrlDefaultsToTheIssuerBaseUrl() {
        SecurityProperties properties = new SecurityProperties();
        properties.setIssuerBaseUrl("https://admin.example/auth");

        assertThat(properties.getJwksBaseUrl()).isEqualTo("https://admin.example/auth");
    }

    /** Blank, not just absent: an unset environment variable arrives as an empty string. */
    @Test
    void aBlankJwksBaseUrlAlsoFallsBack() {
        SecurityProperties properties = new SecurityProperties();
        properties.setIssuerBaseUrl("https://admin.example/auth");
        properties.setJwksBaseUrl("   ");

        assertThat(properties.getJwksBaseUrl()).isEqualTo("https://admin.example/auth");
    }

    @Test
    void anExplicitJwksBaseUrlWins() {
        SecurityProperties properties = new SecurityProperties();
        properties.setIssuerBaseUrl("http://localhost:7070");
        properties.setJwksBaseUrl("http://keycloak:8080");

        assertThat(properties.getJwksBaseUrl()).isEqualTo("http://keycloak:8080");
    }

    /** Defaults describe the testbed stack, matching the datasource default in application.yaml. */
    @Test
    void theDefaultStackRealmIsTheTestbedStack() {
        assertThat(new SecurityProperties().getStackRealm()).isEqualTo("processpuzzle-testbed");
    }
}

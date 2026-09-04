package com.processpuzzle.security;

import com.processpuzzle.core.tenancy.KnownRealms;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Which realms this server trusts, and — just as important — how cheaply it rejects the ones it does
 * not.
 *
 * <p>What they pin down is the gate ordering: an untrusted issuer must be refused by string
 * comparison, before {@link KnownRealms} is consulted at all, because without that this class turns
 * a flood of tokens carrying invented issuers into one call per request against whatever a
 * deployment's adapter is backed by.
 *
 * <p>No JWKS endpoint is running, and the trusted-realm case still resolves — see
 * {@link #theStackRealmIsTrustedWithoutConsultingThePort()} for why that is the assertion and not
 * an accident.
 */
class TenantAuthenticationManagerResolverTest {

    /**
     * Three dot-separated base64url segments, so it gets past the JWT parser and reaches the point of
     * needing a key. The signature is nonsense — irrelevant, because the fetch of the key to check it
     * against is what has to fail here.
     */
    private static final String SIGNED_LOOKING_TOKEN =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6InRlc3Qta2V5In0"
                    + ".eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgxODAvcmVhbG1zL3Byb2Nlc3NwdXp6bGUtYWRtaW4ifQ"
                    + ".c2lnbmF0dXJl";

    private KnownRealms knownRealms;
    private TenantAuthenticationManagerResolver resolver;

    @BeforeEach
    void setUp() {
        knownRealms = mock(KnownRealms.class);
        when(knownRealms.isKnown(anyString())).thenReturn(false);
        resolver = new TenantAuthenticationManagerResolver(
                SecurityTestTokens.properties(), knownRealms);
    }

    /** The load-bearing assertion: rejected without consulting the port. */
    @Test
    void anIssuerOutsideTheConfiguredKeycloakIsRejectedWithoutConsultingTheRealmPort() {
        assertThatThrownBy(() -> resolver.resolve("https://evil.example/realms/my-org"))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("Untrusted");

        verifyNoInteractions(knownRealms);
    }

    @Test
    void anAbsentIssuerIsRejected() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(InvalidBearerTokenException.class);

        verifyNoInteractions(knownRealms);
    }

    /** An issuer under the right host but not naming a realm at all. */
    @Test
    void anIssuerWithNoRealmSegmentIsRejected() {
        assertThatThrownBy(() -> resolver.resolve(SecurityTestTokens.ISSUER_BASE + "/realms/"))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> resolver.resolve(SecurityTestTokens.ISSUER_BASE + "/auth/my-org"))
                .isInstanceOf(InvalidBearerTokenException.class);

        verifyNoInteractions(knownRealms);
    }

    /**
     * A realm name is one path segment. Anything with a slash left in it is a nested path or an
     * attempt to smuggle one past the prefix check, so it is refused before the lookup.
     */
    @Test
    void anIssuerSmugglingAPathIntoTheRealmNameIsRejected() {
        assertThatThrownBy(() -> resolver.resolve(
                SecurityTestTokens.ISSUER_BASE + "/realms/my-org/../" + SecurityTestTokens.STACK_REALM))
                .isInstanceOf(InvalidBearerTokenException.class);

        verifyNoInteractions(knownRealms);
    }

    /** Right prefix, but no such tenant — this is the one case that is allowed to reach the port. */
    @Test
    void anUnknownRealmUnderTheRightPrefixIsRejectedAfterConsultingTheRealmPort() {
        assertThatThrownBy(() -> resolver.resolve(SecurityTestTokens.ISSUER_BASE + "/realms/no-such-org"))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("Unknown realm");

        verify(knownRealms).isKnown("no-such-org");
    }

    /**
     * A miss is not cached, because the usual reason for one is that the organization was provisioned
     * a moment ago — remembering it would leave a new tenant unable to log in until a restart.
     */
    @Test
    void aMissIsNotCachedSoANewlyProvisionedTenantWorksWithoutARestart() {
        assertThatThrownBy(() -> resolver.resolve(SecurityTestTokens.ISSUER_BASE + "/realms/new-org"))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> resolver.resolve(SecurityTestTokens.ISSUER_BASE + "/realms/new-org"))
                .isInstanceOf(InvalidBearerTokenException.class);

        verify(knownRealms, org.mockito.Mockito.times(2)).isKnown("new-org");
    }

    /**
     * The realm this deployment serves is trusted without the port being asked — it is configuration,
     * it is the one realm that is not a tenant, and it has to stay trusted in a deployment that wires
     * no adapter at all.
     */
    @Test
    void theStackRealmIsTrustedWithoutConsultingThePort() {
        // It also resolves, rather than throwing: the decoder is now built lazily, which is the whole
        // reason the /platform/** 500 is gone. The old JwtDecoders.fromIssuerLocation fetched the
        // discovery document right here, from an origin unreachable inside a container, and threw
        // IllegalArgumentException — not an AuthenticationException, so it escaped the security chain
        // as a raw 500 on every request. Nothing listens on the fixtures port, so this assertion
        // failing again would mean construction had gone back to touching the network.
        AuthenticationManager manager = resolver.resolve(
                SecurityTestTokens.ISSUER_BASE + "/realms/" + SecurityTestTokens.STACK_REALM);

        assertThat(manager).isNotNull();
        verify(knownRealms, never()).isKnown(anyString());
    }

    /**
     * The configuration this deployment actually runs with, now that no adapter is wired.
     * {@link KnownRealms#NONE} is not a stand-in for a missing bean — for a single-realm stack it is
     * the complete and correct answer — so a token from any other realm must be refused even though
     * its issuer prefix is right.
     */
    @Test
    void withTheDefaultPortOnlyTheStackRealmIsTrusted() {
        TenantAuthenticationManagerResolver singleRealm = new TenantAuthenticationManagerResolver(
                SecurityTestTokens.properties(), KnownRealms.NONE);

        assertThat(singleRealm.resolve(
                SecurityTestTokens.ISSUER_BASE + "/realms/" + SecurityTestTokens.STACK_REALM))
                .isNotNull();
        assertThatThrownBy(() -> singleRealm.resolve(
                SecurityTestTokens.ISSUER_BASE + "/realms/some-tenant"))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("Unknown realm");
    }

    /** A JWKS base URL that cannot form a URI is a misconfiguration, and must not read as a 500. */
    @Test
    void anUnusableJwksBaseUrlIsRefusedAsAnAuthenticationFailure() {
        SecurityProperties broken = SecurityTestTokens.properties();
        broken.setJwksBaseUrl("not a url");
        TenantAuthenticationManagerResolver brokenResolver =
                new TenantAuthenticationManagerResolver(broken, knownRealms);

        assertThatThrownBy(() -> brokenResolver.resolve(
                SecurityTestTokens.ISSUER_BASE + "/realms/" + SecurityTestTokens.STACK_REALM))
                .isInstanceOf(AuthenticationException.class);
    }

    /**
     * The actual shape of the {@code /platform/organizations} 500, and the assertion that keeps it
     * from coming back.
     *
     * <p>Making the decoder lazy was not enough on its own: it moved the failure from construction to
     * decode, where {@code JwtAuthenticationProvider} raises {@code AuthenticationServiceException} —
     * and {@code AuthenticationEntryPointFailureHandler} <em>rethrows</em> that by design, so it left
     * the filter chain as a raw container 500 with a Boot error page. What has to arrive instead is an
     * {@code OAuth2AuthenticationException}, because that one is not rethrown; the 503 it carries is
     * then written as JSON by {@code ApiSecurityErrorHandler}.
     *
     * <p>Port 1 is the unreachable IdP: nothing can be listening there, so the JWKS fetch fails
     * immediately rather than on a timeout.
     */
    @Test
    void anUnreachableIdentityProviderIsA503AndNotAServiceException() {
        SecurityProperties unreachable = SecurityTestTokens.properties();
        unreachable.setJwksBaseUrl("http://127.0.0.1:1");
        AuthenticationManager manager =
                new TenantAuthenticationManagerResolver(unreachable, knownRealms).resolve(
                        SecurityTestTokens.ISSUER_BASE + "/realms/" + SecurityTestTokens.STACK_REALM);

        assertThatThrownBy(() -> manager.authenticate(
                new BearerTokenAuthenticationToken(SIGNED_LOOKING_TOKEN)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .isNotInstanceOf(AuthenticationServiceException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError())
                .asInstanceOf(InstanceOfAssertFactories.type(BearerTokenError.class))
                .satisfies(error -> {
                    assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.getErrorCode()).isEqualTo("server_error");
                });
    }
}

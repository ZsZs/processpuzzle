package com.processpuzzle.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three payloads the filter chain may write, asserted on the JSON rather than on the Java object:
 * the reason this class exists is that a frontend reads {@code errorText} out of a body, so a test
 * that checked an {@code ApiError} instance would pass even if the wire shape were wrong.
 *
 * <p>The {@code WWW-Authenticate} assertions are not incidental. They are what proves the delegate
 * still runs — drop it and the 401 keeps passing its body assertion while silently ceasing to be an
 * RFC 6750 response, and the description saying <em>why</em> a token was refused disappears with it.
 */
class ApiSecurityErrorHandlerTest {

    private final ApiSecurityErrorHandler handler = new ApiSecurityErrorHandler(JsonMapper.builder().build());
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void missingCredentialIsAnAuthenticationRequiredBody() throws Exception {
        handler.commence(request, response, new InsufficientAuthenticationException("no token"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).isEqualTo(
                "{\"errorId\":\"security.authentication-required\","
                        + "\"errorText\":\"Authentication is required to access this resource.\"}");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    @Test
    void rejectedTokenKeepsTheReasonInTheChallengeAndNotInTheBody() throws Exception {
        handler.commence(request, response, new OAuth2AuthenticationException(
                new BearerTokenError(BearerTokenErrorCodes.INVALID_TOKEN, HttpStatus.UNAUTHORIZED,
                        "Jwt expired at 2026-09-01T00:00:00Z", null)));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentAsString())
                .as("an expired token is still 'authentication required' to a client; the credential's "
                        + "own fault belongs in the challenge")
                .contains("security.authentication-required")
                .doesNotContain("Jwt expired");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .contains("error=\"invalid_token\"")
                .contains("Jwt expired at 2026-09-01T00:00:00Z");
    }

    @Test
    void insufficientScopeReadsAsAccessDeniedEvenThroughTheAuthenticationPath() throws Exception {
        handler.commence(request, response, new OAuth2AuthenticationException(
                new BearerTokenError(BearerTokenErrorCodes.INSUFFICIENT_SCOPE, HttpStatus.FORBIDDEN,
                        "higher privileges required", null)));

        assertThat(response.getStatus())
                .as("the delegate decides the status from the BearerTokenError")
                .isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString())
                .as("the body must never contradict the status line")
                .contains("security.access-denied");
    }

    /**
     * The one refusal that is not about the caller: a token this server could not verify because the
     * identity provider was unreachable. Spring would let that escape the filter chain as a raw 500
     * with a Boot error page — see {@code TenantAuthenticationManagerResolver.unavailable} for why it
     * is reclassified rather than left alone.
     */
    @Test
    void anUnreachableIdentityProviderIsA503WithItsOwnErrorId() throws Exception {
        handler.commence(request, response, new OAuth2AuthenticationException(
                new BearerTokenError("server_error", HttpStatus.SERVICE_UNAVAILABLE,
                        "The identity provider could not be reached to verify this token.", null)));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.getContentAsString())
                .as("a client must be able to tell a wrong credential from one this server could "
                        + "not check, because only the second is worth retrying")
                .contains("security.identity-provider-unavailable")
                .doesNotContain("security.authentication-required");
    }

    @Test
    void missingAuthorityIsAnAccessDeniedBody() throws Exception {
        handler.handle(request, response, new AccessDeniedException("no platform-admin"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).isEqualTo(
                "{\"errorId\":\"security.access-denied\","
                        + "\"errorText\":\"You are not allowed to access this resource.\"}");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    @Test
    void anAlreadyCommittedResponseIsLeftAlone() throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.flushBuffer();

        handler.commence(request, response, new InsufficientAuthenticationException("no token"));

        assertThat(response.getContentAsString())
                .as("writing a second body onto a flushed response corrupts it")
                .isEmpty();
    }
}

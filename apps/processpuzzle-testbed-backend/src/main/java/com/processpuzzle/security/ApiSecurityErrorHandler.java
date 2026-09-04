package com.processpuzzle.security;

import com.processpuzzle.core.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Gives Spring Security's own refusals the error body every other ProcessPuzzle response has.
 *
 * <p>Out of the box a 401 or 403 from the filter chain carries a status line and nothing else, because
 * no controller ran and so no {@code @RestControllerAdvice} saw it. The frontend's
 * {@code httpErrorMessage()} reads {@code errorText} out of the body, so those two statuses were the
 * only ones that reached a user as an empty snackbar. This writes:
 *
 * <pre>
 * 401 {"errorId": "security.authentication-required",      "errorText": "Authentication is required to access this resource."}
 * 403 {"errorId": "security.access-denied",                "errorText": "You are not allowed to access this resource."}
 * 503 {"errorId": "security.identity-provider-unavailable", "errorText": "The identity provider could not be reached, ..."}
 * </pre>
 *
 * <p>The 503 is the one that is not about the caller. A token this server cannot <em>check</em> — because
 * Keycloak is down, or {@code jwks-base-url} is wrong — is a server-side failure, and Spring says so by
 * letting {@code AuthenticationServiceException} escape the bearer-token filter
 * ({@code AuthenticationEntryPointFailureHandler} rethrows it on purpose). Escaping means a raw
 * container 500 with a Boot error page and no {@code errorId}. {@link TenantAuthenticationManagerResolver}
 * therefore reclassifies it into a {@code BearerTokenError} carrying {@code SERVICE_UNAVAILABLE}, which
 * comes back through here as a body a client can read and retry on.
 *
 * <p>Registered in <em>two</em> places by {@link SecurityConfig}, which is the part worth knowing:
 * {@code exceptionHandling()} covers the {@code ExceptionTranslationFilter} path — no credential for a
 * protected path, or an authenticated caller without the authority — while
 * {@code oauth2ResourceServer()} keeps its <em>own</em> entry point for tokens it rejects while
 * validating them (expired, wrong signature, unknown issuer). Only the first of those is what
 * {@code exceptionHandling} configures, so registering it there alone still leaves a bodyless 401 for
 * every stale token — the single most likely 401 in a running system.
 *
 * <p>Each method delegates to the RFC 6750 handler first and only then writes the body. That is not
 * decoration: the delegate contributes the {@code WWW-Authenticate} challenge, which is the only place
 * the <em>reason</em> a token was refused survives ({@code error="invalid_token", error_description=
 * "Jwt expired at ..."}). We deliberately do not put that reason in {@code errorText} — it describes
 * the credential, and the two texts above are all a client should key on — but discarding it entirely
 * would leave an expired token indistinguishable from a missing one when reading a browser's network
 * tab. Neither delegate commits the response, so the body still goes out after them.
 *
 * <p>The status is read back from the delegate rather than assumed, so the body can never contradict
 * the status line: a {@code BearerTokenError} carrying {@code insufficient_scope} makes the entry point
 * answer 403, and that request must read as access-denied even though it arrived through the
 * authentication path.
 */
class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String AUTHENTICATION_REQUIRED = "security.authentication-required";
    static final String ACCESS_DENIED = "security.access-denied";
    static final String IDENTITY_PROVIDER_UNAVAILABLE = "security.identity-provider-unavailable";

    private final ObjectMapper json;
    private final BearerTokenAuthenticationEntryPoint challenge = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler denial = new BearerTokenAccessDeniedHandler();

    /**
     * @param json the application's Jackson 3 mapper. Boot 4 contributes no Jackson 2
     *             {@code ObjectMapper} bean, and this body has to look like the ones the message
     *             converters write.
     */
    ApiSecurityErrorHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        challenge.commence(request, response, exception);
        writeBody(response);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        denial.handle(request, response, exception);
        writeBody(response);
    }

    private void writeBody(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), bodyFor(response.getStatus()));
    }

    /**
     * The status the delegate settled on decides the message, so the body can never contradict the
     * status line.
     */
    private static ApiError bodyFor(int status) {
        if (status == HttpStatus.FORBIDDEN.value()) {
            return new ApiError(ACCESS_DENIED, "You are not allowed to access this resource.");
        }
        if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            return new ApiError(IDENTITY_PROVIDER_UNAVAILABLE,
                    "The identity provider could not be reached, so your credentials could not be "
                            + "verified. Please try again shortly.");
        }
        return new ApiError(AUTHENTICATION_REQUIRED, "Authentication is required to access this resource.");
    }
}

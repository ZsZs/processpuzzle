package com.processpuzzle.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * How much of the API this deployment closes, bound from {@code processpuzzle.security.*}.
 *
 * <p>{@link #requireAuthentication} defaults to {@code false}, and that default is a deliberate,
 * temporary compromise rather than an oversight. The Angular applications do not send a bearer token
 * yet: turning it on today would answer 401 to every existing screen and every Playwright test. So
 * the resource server is wired, tokens are validated and tenant isolation is enforced <em>when a
 * token is present</em>, while the legacy tenant API stays reachable without one.
 *
 * <p>What is <b>not</b> optional: the {@code /platform/**} staff surface always requires the
 * {@code platform-admin} authority, and the org-admin user-management paths always require
 * authentication. Those are new, so nothing depends on them being open, and they are the two that
 * delete tenants and manage credentials.
 *
 * <p>Set {@code processpuzzle.security.require-authentication: true} once a deployment's clients send
 * tokens. Any deployment reachable from the internet must.
 */
@Component
@ConfigurationProperties(prefix = "processpuzzle.security")
public class SecurityProperties {

    /** Whether the tenant API requires a bearer token. See the class comment before changing. */
    private boolean requireAuthentication = false;

    /**
     * Base URL of the Keycloak server whose realms are trusted, as it appears in a token's {@code iss}
     * claim — which is the origin the <em>browser</em> was redirected to. An issuer that does not start
     * with this is rejected without a database lookup, which is what keeps a flood of tokens carrying
     * invented issuers from becoming a query per request.
     */
    private String issuerBaseUrl = "http://localhost:7070";

    /**
     * Base URL this <em>server</em> fetches signing keys from. Defaults to {@link #issuerBaseUrl},
     * which is right for any deployment where the browser and this container reach Keycloak through
     * the same origin — the prod topology, where nginx fronts both under {@code /auth}.
     *
     * <p>It is a separate property because in a container topology the two genuinely differ: the
     * browser is redirected to {@code http://localhost:7070}, which inside the container resolves to
     * the container itself, while the reachable address is {@code http://keycloak:8080}. Keeping them
     * in one property meant either a token whose issuer did not match or a JWKS URL that did not
     * resolve, and the second one is what returned 500 on every authenticated request.
     */
    private String jwksBaseUrl;

    /**
     * The realm this deployment serves — one backend instance per application stack, so exactly one
     * realm is trusted besides the tenant realms. See docs/application-stacks.md.
     *
     * <p>Called {@code platform-realm} until it had two possible values: the name only read correctly
     * for the admin stack, while the meaning ("the realm this instance serves") never changed. Which
     * authority may reach {@code /platform/**} is a different question, answered by
     * {@link #platformAdminAuthority}.
     */
    private String stackRealm = "processpuzzle-testbed";

    /** Authority a staff token must hold. Also the realm role of the same name in the platform realm. */
    private String platformAdminAuthority = "platform-admin";

    // Hand-written accessors rather than Lombok: this application module, unlike the libraries, does
    // not have Lombok on its classpath, and adding it for one properties class is not worth it.

    public boolean isRequireAuthentication() {
        return requireAuthentication;
    }

    public void setRequireAuthentication(boolean requireAuthentication) {
        this.requireAuthentication = requireAuthentication;
    }

    public String getIssuerBaseUrl() {
        return issuerBaseUrl;
    }

    public void setIssuerBaseUrl(String issuerBaseUrl) {
        this.issuerBaseUrl = issuerBaseUrl;
    }

    /** {@link #issuerBaseUrl} when unset, so a single-origin deployment needs no configuration. */
    public String getJwksBaseUrl() {
        return jwksBaseUrl == null || jwksBaseUrl.isBlank() ? issuerBaseUrl : jwksBaseUrl;
    }

    public void setJwksBaseUrl(String jwksBaseUrl) {
        this.jwksBaseUrl = jwksBaseUrl;
    }

    public String getStackRealm() {
        return stackRealm;
    }

    public void setStackRealm(String stackRealm) {
        this.stackRealm = stackRealm;
    }

    public String getPlatformAdminAuthority() {
        return platformAdminAuthority;
    }

    public void setPlatformAdminAuthority(String platformAdminAuthority) {
        this.platformAdminAuthority = platformAdminAuthority;
    }
}

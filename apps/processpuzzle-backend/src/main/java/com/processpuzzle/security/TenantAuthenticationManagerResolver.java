package com.processpuzzle.security;

import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which realm's keys a bearer token is validated against, by its {@code iss} claim.
 *
 * <p>Realm-per-tenant means there is no single issuer to configure, and the set grows at run time as
 * organizations are provisioned — so a static issuer list cannot work, and this resolver is what
 * {@code JwtIssuerAuthenticationManagerResolver} is designed to be handed.
 *
 * <h2>Three gates, cheapest first</h2>
 *
 * <ol>
 *   <li><b>Prefix.</b> The issuer must sit under the configured Keycloak base URL. A string
 *       comparison, and it is what stops a flood of tokens carrying invented issuers from becoming a
 *       database query per request — without it this class is a denial-of-service amplifier.
 *   <li><b>Cache.</b> A realm already resolved is answered from the map, so the common case touches
 *       neither the database nor the network.
 *   <li><b>Realm exists.</b> The realm must be this deployment's own stack realm or an existing
 *       organization key. Only an unrecognised realm that already passed the prefix gate reaches the
 *       database.
 * </ol>
 *
 * <h2>Two URLs for one Keycloak</h2>
 *
 * <p>The issuer a token carries and the address this server fetches signing keys from are configured
 * separately — see {@link SecurityProperties#getJwksBaseUrl()}. This class is where that split
 * matters: the decoder is built from the JWKS URL and validates the {@code iss} claim against the
 * issuer URL, which are the same string in a single-origin deployment and deliberately different in a
 * container one.
 *
 * <p>It used to be one URL and {@code JwtDecoders.fromIssuerLocation(issuer)}. That fetches the
 * discovery document eagerly, from the browser-facing origin — which inside the container is the
 * container itself — and fails with an {@link IllegalArgumentException}. Not being an
 * {@code AuthenticationException}, it escaped the security chain as a raw 500 on <em>every</em>
 * request. Pointing {@code fromIssuerLocation} at the internal URL instead does not help: Keycloak
 * advertises the browser-facing origin as {@code "issuer"} however the document is fetched, so its
 * built-in issuer check would then reject every real token.
 *
 * <p><b>Only successes are cached.</b> A miss is not remembered, because the usual reason for one is
 * that the organization was provisioned a moment ago — caching negatives would leave a new tenant
 * unable to log in until a restart. The cache is unbounded, which is safe for the same reason: only
 * realms that exist ever enter it, so its size is the number of tenants and not the number of
 * requests.
 */
public class TenantAuthenticationManagerResolver implements AuthenticationManagerResolver<String> {

    private static final Logger LOG = LoggerFactory.getLogger(TenantAuthenticationManagerResolver.class);
    /** Keycloak's JWKS endpoint, relative to a realm. */
    private static final String CERTS_PATH = "/protocol/openid-connect/certs";

    private final SecurityProperties properties;
    private final OrganizationRepository organizations;
    private final Map<String, AuthenticationManager> byRealm = new ConcurrentHashMap<>();

    public TenantAuthenticationManagerResolver(SecurityProperties properties,
                                               OrganizationRepository organizations) {
        this.properties = properties;
        this.organizations = organizations;
    }

    @Override
    public AuthenticationManager resolve(String issuer) {
        String realm = realmOf(issuer);
        if (realm == null) {
            throw new InvalidBearerTokenException("Untrusted token issuer: " + issuer);
        }
        AuthenticationManager cached = byRealm.get(realm);
        if (cached != null) {
            return cached;
        }
        if (!isKnownRealm(realm)) {
            throw new InvalidBearerTokenException("Unknown realm in token issuer: " + issuer);
        }
        return byRealm.computeIfAbsent(realm, name -> managerFor(issuer, name));
    }

    /**
     * The realm name from an issuer of the form {@code <base>/realms/<realm>}, or {@code null} when
     * the issuer is not under the configured base URL.
     */
    private String realmOf(String issuer) {
        if (issuer == null) {
            return null;
        }
        String expectedPrefix = trimTrailingSlash(properties.getIssuerBaseUrl()) + SecurityConstants.REALMS_SEGMENT;
        if (!issuer.startsWith(expectedPrefix)) {
            return null;
        }
        String realm = issuer.substring(expectedPrefix.length());
        // A realm name is one path segment. Anything with a slash left in it is a nested path or an
        // attempt to smuggle one, and either way not a realm this server issued.
        return realm.isBlank() || realm.contains("/") ? null : realm;
    }

    private boolean isKnownRealm(String realm) {
        return properties.getStackRealm().equals(realm) || organizations.existsById(realm);
    }

    private AuthenticationManager managerFor(String issuer, String realm) {
        String jwkSetUri = trimTrailingSlash(properties.getJwksBaseUrl())
                + SecurityConstants.REALMS_SEGMENT + realm + CERTS_PATH;
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder(issuer, jwkSetUri));
        provider.setJwtAuthenticationConverter(new RealmRoleConverter());
        // After construction, not before: the old log line claimed success on the line immediately
        // preceding the one that threw, so the logs said a realm was trusted and then 500ed.
        LOG.info("Trusting tokens from realm '{}' issued at {}, signing keys from {}.",
                realm, issuer, jwkSetUri);
        return provider::authenticate;
    }

    /**
     * A decoder that fetches keys from {@code jwkSetUri} and requires the {@code iss} claim to equal
     * {@code issuer} — the check {@code fromIssuerLocation} would otherwise have installed from the
     * discovery document.
     *
     * <p>Lazy on purpose, and that laziness is half the fix: nothing here touches the network, so an
     * IdP that happens to be down cannot fail construction and cannot poison the cache — the fetch
     * happens per decode and recovers on its own.
     *
     * <p>The URL is nevertheless parsed eagerly. Nimbus does not look at that string until the first
     * token arrives, so a mistyped {@code jwks-base-url} would otherwise surface as an opaque "failed
     * to decode the Jwt" on every request with the offending URL nowhere in the logs. Checking it here
     * turns a permanent misconfiguration into one log line naming the URL.
     *
     * <p>The returned decoder wraps the delegate, which is the other half — see {@link #unavailable}.
     */
    private JwtDecoder decoder(String issuer, String jwkSetUri) {
        NimbusJwtDecoder delegate;
        try {
            new URI(jwkSetUri).toURL();
            delegate = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            delegate.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        } catch (URISyntaxException | MalformedURLException | RuntimeException e) {
            LOG.error("Cannot validate tokens from {}: the signing keys were to be read from {}, "
                    + "which is not a usable URL. Check processpuzzle.security.jwks-base-url.",
                    issuer, jwkSetUri, e);
            throw new AuthenticationServiceException(
                    "Cannot validate tokens from " + issuer + ": the identity provider at "
                            + jwkSetUri + " is not usable.", e);
        }
        return token -> {
            try {
                return delegate.decode(token);
            } catch (BadJwtException badToken) {
                // The token itself is at fault: expired, wrong signature, wrong issuer. Spring turns
                // this into a 401 invalid_token, which is exactly right.
                throw badToken;
            } catch (JwtException cannotCheck) {
                throw unavailable(jwkSetUri, cannotCheck);
            }
        };
    }

    /**
     * A token this server cannot <em>check</em> is a server-side failure, and has to read as one.
     *
     * <p>Spring agrees about the classification — {@code JwtAuthenticationProvider} turns any
     * non-{@code BadJwtException} into an {@code AuthenticationServiceException} — but not about the
     * response: {@code AuthenticationEntryPointFailureHandler} deliberately <em>rethrows</em> that
     * exception instead of calling the entry point, so it leaves the filter chain as a raw container
     * 500 carrying a Boot error page and no {@code errorId} the frontend can read. That is the exact
     * shape the {@code /platform/organizations} bug had, and making the decoder lazy does not by
     * itself change it: laziness only moves the failure from construction to decode.
     *
     * <p>Reclassifying it as a {@code BearerTokenError} with {@code SERVICE_UNAVAILABLE} keeps the
     * truth — 503 and not 401, because the caller's credential may be perfectly good — while routing
     * it through {@link ApiSecurityErrorHandler}, since an {@code OAuth2AuthenticationException} is
     * not rethrown. Logged at error with the URL: this is an operator problem, not a user one.
     */
    private static OAuth2AuthenticationException unavailable(String jwkSetUri, JwtException cause) {
        LOG.error("Cannot verify a bearer token: reading signing keys from {} failed. The identity "
                + "provider is unreachable, or processpuzzle.security.jwks-base-url is wrong.",
                jwkSetUri, cause);
        return new OAuth2AuthenticationException(
                new BearerTokenError(OAuth2ErrorCodes.SERVER_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
                        "The identity provider could not be reached to verify this token.", null),
                cause);
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

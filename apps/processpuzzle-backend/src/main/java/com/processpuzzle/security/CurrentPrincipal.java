package com.processpuzzle.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the authenticated principal out of the security context: which realm issued its token, and
 * what it may do.
 *
 * <p>One place, so that "which tenant is this token for" is answered the same way everywhere. The
 * answer comes from the token's {@code iss} claim rather than from any tenant claim Keycloak might be
 * configured to add — the issuer is signed by the realm that issued it and cannot name a different
 * realm, whereas a claim can be whatever a realm's mapper was told to put there.
 */
@Component
public class CurrentPrincipal {

    private static final String REALMS_SEGMENT = "/realms/";

    private final SecurityProperties properties;

    public CurrentPrincipal(SecurityProperties properties) {
        this.properties = properties;
    }

    /** Whether there is an authenticated bearer token at all. */
    public boolean isAuthenticated() {
        return jwt().isPresent();
    }

    /**
     * The realm the current token was issued by, or empty when unauthenticated.
     *
     * <p>For a tenant's token this equals the organization's key, because that is how realms are
     * named. For staff it is the platform realm, which is no tenant.
     */
    public Optional<String> realm() {
        return jwt().map(Jwt::getIssuer)
                .map(Object::toString)
                .map(CurrentPrincipal::realmOf);
    }

    /** Whether the principal is ProcessPuzzle staff. */
    public boolean isPlatformAdmin() {
        return authorities().contains(properties.getPlatformAdminAuthority());
    }

    /** Whether the principal is a member of {@code orgKey} — i.e. its token came from that realm. */
    public boolean isMemberOf(String orgKey) {
        return realm().filter(realm -> realm.equals(orgKey)).isPresent();
    }

    /** The realm roles the token carries, as plain names. */
    public Set<String> authorities() {
        return authentication()
                .map(Authentication::getAuthorities)
                .map(CurrentPrincipal::names)
                .orElseGet(Set::of);
    }

    private static Set<String> names(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private Optional<Jwt> jwt() {
        return authentication()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(JwtAuthenticationToken::getToken);
    }

    private Optional<Authentication> authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                ? Optional.of(authentication)
                : Optional.empty();
    }

    private static String realmOf(String issuer) {
        int at = issuer.lastIndexOf(REALMS_SEGMENT);
        return at < 0 ? issuer : issuer.substring(at + REALMS_SEGMENT.length());
    }
}

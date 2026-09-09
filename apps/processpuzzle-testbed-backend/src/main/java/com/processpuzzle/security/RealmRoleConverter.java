package com.processpuzzle.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a Keycloak token's {@code realm_access.roles} into Spring authorities.
 *
 * <p><b>No {@code ROLE_} prefix.</b> The authorities are the realm role names verbatim, because the
 * same strings are already this platform's role vocabulary in three other places:
 * {@code NavNode.roles} in an app definition, {@code RoleDefinition} in a workflow, and the
 * {@code org-admin} / {@code org-member} pair created with every realm. Prefixing here would mean
 * translating in every one of those comparisons, and the first place someone forgot would fail open.
 * {@code hasAuthority} rather than {@code hasRole} is used throughout for the same reason.
 *
 * <p>Keycloak also carries per-client roles under {@code resource_access.<client>.roles}. Deliberately
 * ignored: this platform grants realm roles, and reading both would let one role name mean two things
 * depending on which claim it arrived in.
 */
public class RealmRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authoritiesOf(jwt), jwt.getSubject());
    }

    /** The realm roles a token carries, as authorities. Empty when the claim is absent or malformed. */
    static Collection<GrantedAuthority> authoritiesOf(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String role : rolesOf(jwt)) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        return authorities;
    }

    @SuppressWarnings("unchecked")
    private static List<String> rolesOf(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get(REALM_ACCESS);
        if (!(realmAccess instanceof Map<?, ?> claim)) {
            return List.of();
        }
        Object roles = ((Map<String, Object>) claim).get(ROLES);
        if (!(roles instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}

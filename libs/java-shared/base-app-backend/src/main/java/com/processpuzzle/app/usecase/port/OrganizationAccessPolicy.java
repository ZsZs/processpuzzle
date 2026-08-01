package com.processpuzzle.app.usecase.port;

import java.util.Collection;

/**
 * Outbound port deciding what the current principal may do with a tenant's metadata. Supplied by
 * the deploying application, which is where identity actually lives — there is no Spring Security
 * on the classpath of this library.
 *
 * <p>Every method defaults to permitting, so an implementation only has to override what it
 * actually enforces. That direction matters for {@link #hasAnyRole}: a port shaped as
 * {@code Set<String> currentRoles()} would return an empty set without an identity provider, and
 * every nav item with roles configured would then be filtered away — denying access precisely in
 * the apps someone had bothered to configure.
 *
 * @see PermitAllOrganizationAccessPolicy
 */
public interface OrganizationAccessPolicy {

    /**
     * Rejects the call when the principal is not a member of {@code orgKey}. The contract requires
     * 403 here: the {@code orgKey} path segment is not an authorization decision on its own, and
     * without this check editing the URL is enough to read another tenant's metadata.
     *
     * @throws com.processpuzzle.app.usecase.exception.OrganizationAccessDeniedException when denied
     */
    default void requireAccess(String orgKey) {
        // permitted by default
    }

    /**
     * Rejects the call when the principal may not author metadata for {@code orgKey} — used for the
     * designer's draft preview.
     *
     * @throws com.processpuzzle.app.usecase.exception.OrganizationAccessDeniedException when denied
     */
    default void requireDesign(String orgKey) {
        // permitted by default
    }

    /**
     * Whether the principal holds at least one of {@code requiredRoles}. An empty or {@code null}
     * collection means "any authenticated member" and must return {@code true}.
     */
    default boolean hasAnyRole(Collection<String> requiredRoles) {
        return true;
    }
}

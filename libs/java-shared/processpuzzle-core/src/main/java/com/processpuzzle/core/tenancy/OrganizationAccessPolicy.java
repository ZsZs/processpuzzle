package com.processpuzzle.core.tenancy;

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

 * <p>Moved here from {@code base-app} with the {@code Organization} aggregate. base-app still
 * consumes it, through {@code platformadmin :: port} — which is also why the port is exposed
 * separately from {@code usecase}: implementing it is a different act from calling a use case.
 *
 * @see PermitAllOrganizationAccessPolicy
 */
public interface OrganizationAccessPolicy {

    /**
     * Rejects the call when the principal is not a member of {@code orgKey}. The contract requires
     * 403 here: the {@code orgKey} path segment is not an authorization decision on its own, and
     * without this check editing the URL is enough to read another tenant's metadata.
     *
     * @throws com.processpuzzle.core.tenancy.OrganizationAccessDeniedException when denied
     */
    default void requireAccess(String orgKey) {
        // permitted by default
    }

    /**
     * Rejects the call when the principal may not author metadata for {@code orgKey} — used for the
     * designer's draft preview.
     *
     * @throws com.processpuzzle.core.tenancy.OrganizationAccessDeniedException when denied
     */
    default void requireDesign(String orgKey) {
        // permitted by default
    }

    /**
     * Rejects the call when the principal is not ProcessPuzzle staff — the gate on the whole
     * {@code /platform/**} surface.
     *
     * <p>Needed as its own method rather than expressed through {@link #requireAccess}: a platform
     * administrator acts across all tenants, so there is no {@code orgKey} to compare anything
     * against. It is the one authorization question in this port that is not about a tenant.
     *
     * <p>Note this defaults to permitting, like everything else here — which is safe only because
     * {@link PermitAllOrganizationAccessPolicy} exists purely as a development stand-in. A
     * deployment that reaches the internet must supply a real policy; see that class.
     *
     * @throws com.processpuzzle.core.tenancy.OrganizationAccessDeniedException
     *         when denied
     */
    default void requirePlatformAdmin() {
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

package com.processpuzzle.core.tenancy;

import java.util.Collection;

/**
 * Outbound port deciding what the current principal may do with a tenant's metadata. Supplied by
 * the deploying application, which is where identity actually lives — there is no Spring Security
 * on the classpath of this library.
 *
 * <p>Authorization decisions default to denial. Implementations must explicitly grant the
 * operations they support, so adding a new check cannot silently authorize existing policies.
 * Hosts intentionally running without identity may explicitly register
 * {@link PermitAllOrganizationAccessPolicy}.
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
        throw new OrganizationAccessDeniedException(orgKey);
    }

    /**
     * Rejects the call when the principal may not author metadata for {@code orgKey} — used for the
     * designer's draft preview.
     *
     * @throws com.processpuzzle.core.tenancy.OrganizationAccessDeniedException when denied
     */
    default void requireDesign(String orgKey) {
        throw new OrganizationAccessDeniedException(orgKey);
    }

    /**
     * Rejects the call when the principal is not ProcessPuzzle staff — the gate on the whole
     * {@code /platform/**} surface.
     *
     * <p>Needed as its own method rather than expressed through {@link #requireAccess}: a platform
     * administrator acts across all tenants, so there is no {@code orgKey} to compare anything
     * against. It is the one authorization question in this port that is not about a tenant.
     *
     * @throws com.processpuzzle.core.tenancy.OrganizationAccessDeniedException
     *         when denied
     */
    default void requirePlatformAdmin() {
        throw new OrganizationAccessDeniedException("platform");
    }

    /**
     * Whether the principal holds at least one of {@code requiredRoles}. Defaults to denial.
     * The guard handles an empty or {@code null} collection as "any member" after the use case's
     * access check; this method does not itself establish membership.
     */
    default boolean hasAnyRole(Collection<String> requiredRoles) {
        return false;
    }
}

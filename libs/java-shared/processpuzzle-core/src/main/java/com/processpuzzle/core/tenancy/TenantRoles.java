package com.processpuzzle.core.tenancy;

/**
 * The two realm roles ProcessPuzzle itself interprets, as opposed to the arbitrary ones a tenant
 * defines for its own application.
 *
 * <p>Shared vocabulary rather than any one feature's: platform-admin creates these roles when it
 * provisions a realm, org-admin grants and revokes them, and an access policy reads them out of a
 * token. They were constants on {@code IdentityRealmPort}, which meant org-admin imported
 * platform-admin's port — a compile dependency between two features for two string literals.
 */
public final class TenantRoles {

    /** Realm role granting the tenant's own administration API. */
    public static final String ORG_ADMIN = "org-admin";

    /** Realm role every member of a tenant holds. */
    public static final String ORG_MEMBER = "org-member";

    private TenantRoles() {
    }
}

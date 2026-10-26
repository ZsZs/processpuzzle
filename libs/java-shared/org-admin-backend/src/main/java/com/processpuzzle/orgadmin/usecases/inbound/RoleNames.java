package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.tenancy.TenantRoles;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a role may be called, and which names are not the tenant's to use.
 *
 * <p>The name is the key everywhere — in documents, navigation items, workflow role definitions and the
 * token — so the shape is narrow and fixed: lower case, digits, {@code -} and {@code _}, the same
 * alphabet a role definition's id uses. A name that fits it is also safe in a URL path segment, which
 * the identity provider's role endpoints rely on.
 */
public final class RoleNames {

    /** Must match {@code NewOrganizationRole.name} in org-admin-api.yaml. */
    public static final Pattern VALID = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,62}$");

    private static final Set<String> RESERVED = Set.of("platform-admin", "offline_access", "uma_authorization");
    private static final String DEFAULT_ROLES_PREFIX = "default-roles-";

    private RoleNames() {
    }

    public static boolean isValid(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    /**
     * Names ProcessPuzzle or the identity provider own. An author must not mint authority by typing one,
     * and an administrator must not be able to delete one.
     */
    public static boolean isReserved(String name) {
        return TenantRoles.ALL.contains(name) || RESERVED.contains(name) || name.startsWith(DEFAULT_ROLES_PREFIX);
    }
}

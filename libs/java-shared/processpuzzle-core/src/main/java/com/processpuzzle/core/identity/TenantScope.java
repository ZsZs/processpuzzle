package com.processpuzzle.core.identity;

/**
 * Where one tenant lives in Keycloak, as {@link KeycloakTenantDirectory#resolve} found it.
 *
 * <p>Carries the Admin API paths that follow from it, so that callers build their requests from the
 * scope instead of re-deriving {@code /admin/realms/...} from a string they hope is a realm name.
 */
public sealed interface TenantScope {

    /** The realm the tenant's users live in: its own, or the shared organization realm. */
    String realm();

    /** {@code /admin/realms/{realm}}, the root of every Admin API call for this tenant. */
    default String adminRealmPath() {
        return adminRealmPath(realm());
    }

    /** {@code /admin/realms/{realm}} for a realm known by name. */
    static String adminRealmPath(String realm) {
        return "/admin/realms/" + realm;
    }

    /** The tenant is a realm of its own. */
    record RealmScope(String realm) implements TenantScope {
    }

    /**
     * The tenant is an Organization inside a shared realm.
     *
     * @param organizationId Keycloak's id for the organization, which every organization path uses
     * @param alias the organization's alias, which by convention is the tenant's {@code orgKey}
     */
    record OrganizationScope(String realm, String organizationId, String alias) implements TenantScope {

        /** {@code /admin/realms/{realm}/organizations/{organizationId}}. */
        public String organizationPath() {
            return adminRealmPath() + "/organizations/" + organizationId;
        }
    }
}

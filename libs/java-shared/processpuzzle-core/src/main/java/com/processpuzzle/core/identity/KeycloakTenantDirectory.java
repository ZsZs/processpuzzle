package com.processpuzzle.core.identity;

import com.processpuzzle.core.identity.TenantScope.OrganizationScope;
import com.processpuzzle.core.identity.TenantScope.RealmScope;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one place that knows how a tenant maps onto Keycloak — realm-per-tenant or Organization in a
 * shared realm — over {@link KeycloakAdminClient}.
 *
 * <p>base-workflow's role directory and org-admin's user directory both need to find a tenant and
 * its role groups; this is what they share. What they <em>do</em> there, and what a missing tenant
 * means to them, stays theirs: an empty {@link #resolve} is "retry later" to the role projection and
 * a 404 to the user API, so this class reports absence and leaves the verdict to the caller.
 *
 * <p>Failures propagate as {@link IdentityProviderUnavailableException}, as from the client itself.
 */
@Component
public class KeycloakTenantDirectory {

    /** Organizations have a handful of role groups; one page that no tenant will fill. */
    static final int GROUP_PAGE = 500;

    private final KeycloakAdminClient client;

    public KeycloakTenantDirectory(KeycloakAdminClient client) {
        this.client = client;
    }

    /** See {@link KeycloakAdminClient#tenancy()}. */
    public KeycloakTenancy tenancy() {
        return client.tenancy();
    }

    /**
     * Where the tenant named {@code directory} lives.
     *
     * <p>Realm-per-tenant: the realm named {@code directory} — the platform's realm = {@code orgKey}
     * convention, stated here once — which is not looked up. Organization mode: see
     * {@link #organization(String)}. Unconfigured: always empty.
     */
    public Optional<TenantScope> resolve(String directory) {
        return switch (client.tenancy()) {
            case UNCONFIGURED -> Optional.empty();
            case REALM_PER_TENANT -> Optional.of(new RealmScope(directory));
            case ORGANIZATION -> organization(directory).map(TenantScope.class::cast);
        };
    }

    /**
     * The organization carrying {@code alias} in the shared realm, or empty when none does — or when
     * the deployment is not in organization mode at all.
     *
     * <p>{@code exact=true} and an alias comparison both: the search also matches names, and a tenant
     * must never be resolved to a look-alike.
     */
    public Optional<OrganizationScope> organization(String alias) {
        Optional<String> realm = client.organizationRealm();
        if (realm.isEmpty() || alias == null) {
            return Optional.empty();
        }
        String encoded = URLEncoder.encode(alias, StandardCharsets.UTF_8);
        return client.getList(TenantScope.adminRealmPath(realm.get()) + "/organizations?exact=true&search=" + encoded)
                .stream()
                .filter(org -> alias.equals(String.valueOf(org.get("alias"))))
                .map(org -> new OrganizationScope(realm.get(), String.valueOf(org.get("id")), alias))
                .findFirst();
    }

    /**
     * The organization's top-level groups, name → id, in Keycloak's order. A group's {@code path} is
     * relative to the organization, so a top-level one is {@code /<name>}; nested groups are
     * structure, not roles, and are left out.
     */
    public Map<String, String> topLevelGroups(OrganizationScope scope) {
        Map<String, String> groups = new LinkedHashMap<>();
        topLevelGroupRepresentations(scope).forEach(group ->
                groups.putIfAbsent(String.valueOf(group.get("name")), String.valueOf(group.get("id"))));
        return groups;
    }

    /** As {@link #topLevelGroups}, but the full representations, for callers that need more than the id. */
    public List<Map<String, Object>> topLevelGroupRepresentations(OrganizationScope scope) {
        return client.getList(scope.organizationPath() + "/groups?first=0&max=" + GROUP_PAGE).stream()
                .filter(KeycloakTenantDirectory::isTopLevel)
                .toList();
    }

    /** Whether a group representation is top-level within its organization. */
    public static boolean isTopLevel(Map<String, Object> group) {
        return ("/" + group.get("name")).equals(String.valueOf(group.get("path")));
    }

    /** The user's member representation, or empty when they are not a member of this organization. */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> findMember(OrganizationScope scope, String userId) {
        return client.find(scope.organizationPath() + "/members/" + userId, Map.class)
                .map(raw -> (Map<String, Object>) raw);
    }
}

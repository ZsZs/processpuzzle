package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.KeycloakTenantDirectory;
import com.processpuzzle.core.identity.TenantScope.OrganizationScope;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownOrganizationException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownRoleException;
import com.processpuzzle.orgadmin.usecases.outbound.RoleCatalogPort;
import org.springframework.http.HttpMethod;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.processpuzzle.orgadmin.adapters.outbound.KeycloakUserDirectoryAdapter.wrap;

/**
 * The role catalog of a tenant that is a Keycloak Organization: a role is a top-level group of the
 * organization, the same representation base-workflow's projection writes, so a role an administrator
 * defines is indistinguishable in the token from one the designer produced.
 *
 * <p>{@link #ensureRoles} does nothing, deliberately. An author's reference must not mint a role in a
 * shared realm; the administrator defines it, and the pickers only offer what exists.
 *
 * <p>Deliberately not a {@code @Component}: {@code UserDirectoryConfiguration} picks it.
 */
public class KeycloakOrganizationRoleCatalogAdapter implements RoleCatalogPort {

    private final KeycloakAdminClient client;
    private final KeycloakTenantDirectory tenants;

    public KeycloakOrganizationRoleCatalogAdapter(KeycloakAdminClient client, KeycloakTenantDirectory tenants) {
        this.client = client;
        this.tenants = tenants;
    }

    @Override
    public void ensureRoles(String directory, Collection<String> roleNames, String description) {
        // A reference does not mint a role here; see the class comment.
    }

    @Override
    public void createRole(String directory, String name, String description) {
        OrganizationScope org = organization(directory);
        wrap(() -> client.exchange(HttpMethod.POST, groupsPath(org), representation(name, description), null));
    }

    @Override
    public void updateDescription(String directory, String name, String description) {
        OrganizationScope org = organization(directory);
        String groupId = wrap(() -> tenants.topLevelGroups(org)).get(name);
        if (groupId == null) {
            throw new UnknownRoleException(directory, name);
        }
        wrap(() -> client.exchange(HttpMethod.PUT, groupsPath(org) + "/" + groupId, representation(name, description), null));
    }

    @Override
    public void deleteRole(String directory, String name) {
        OrganizationScope org = organization(directory);
        String groupId = wrap(() -> tenants.topLevelGroups(org)).get(name);
        if (groupId != null) {
            wrap(() -> client.exchange(HttpMethod.DELETE, groupsPath(org) + "/" + groupId, null, null));
        }
    }

    @Override
    public boolean hasHolders(String directory, String name) {
        OrganizationScope org = organization(directory);
        String groupId = wrap(() -> tenants.topLevelGroups(org)).get(name);
        return groupId != null
                && !wrap(() -> client.getList(groupsPath(org) + "/" + groupId + "/members?first=0&max=1")).isEmpty();
    }

    private OrganizationScope organization(String alias) {
        return wrap(() -> tenants.organization(alias)).orElseThrow(() -> new UnknownOrganizationException(alias));
    }

    private static String groupsPath(OrganizationScope org) {
        return org.organizationPath() + "/groups";
    }

    private static Map<String, Object> representation(String name, String description) {
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("name", name);
        representation.put("description", description);
        return representation;
    }
}

package com.processpuzzle.workflow.definition.adapters.outbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.KeycloakTenantDirectory;
import com.processpuzzle.core.identity.TenantScope.OrganizationScope;
import com.processpuzzle.workflow.definition.usecases.outbound.RoleDirectoryPort;
import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects the role catalog into Keycloak <b>Organization groups</b>, for deployments where every
 * tenant is an Organization inside one shared realm ({@code keycloak.admin.organization-realm}).
 *
 * <p>Realm roles cannot carry a tenant's roles there: the realm is every customer's, so one
 * customer's {@code reviewer} would be every customer's. An organization's groups are its own
 * (Keycloak 26.6+) — same name, different group, and Keycloak refuses to put a non-member into one —
 * and a member's groups reach the token under that organization, via the Organization Group
 * Membership mapper keycloak-init installs:
 *
 * <pre>"organization": { "acme": { "groups": ["/reviewer"] } }</pre>
 *
 * <p>Finding the organization and its top-level groups is core's {@link KeycloakTenantDirectory};
 * this adapter adds only what a role is. A role is a <em>top-level</em> group named after
 * {@code RoleDefinition.id}; nested groups are left to whoever made them. Same contract as
 * {@link KeycloakRoleDirectoryAdapter} otherwise: list-then-PUT-or-POST with the create's 409
 * tolerated, and an absent group on delete is success.
 *
 * <p>An {@code orgKey} no organization carries raises {@link IdentityProviderUnavailableException},
 * which {@code SyncRoleDirectory} already treats as "retry at the next reconcile" — the organization
 * may simply not have been provisioned yet.
 */
public class KeycloakOrganizationRoleDirectoryAdapter implements RoleDirectoryPort {

    private final KeycloakAdminClient client;
    private final KeycloakTenantDirectory tenants;

    public KeycloakOrganizationRoleDirectoryAdapter(KeycloakAdminClient client, KeycloakTenantDirectory tenants) {
        this.client = client;
        this.tenants = tenants;
    }

    @Override
    public void upsertRole(String orgKey, String roleName, String description) {
        OrganizationScope org = organization(orgKey);
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("name", roleName);
        representation.put("description", description);

        String existing = tenants.topLevelGroups(org).get(roleName);
        if (existing != null) {
            client.exchange(HttpMethod.PUT, groupsPath(org) + "/" + existing, representation, null);
        } else {
            client.exchangeTolerating(HttpMethod.POST, groupsPath(org), representation,
                    KeycloakAdminClient.HTTP_CONFLICT);
        }
    }

    @Override
    public void deleteRole(String orgKey, String roleName) {
        OrganizationScope org = organization(orgKey);
        String existing = tenants.topLevelGroups(org).get(roleName);
        if (existing != null) {
            client.exchange(HttpMethod.DELETE, groupsPath(org) + "/" + existing, null, null);
        }
    }

    @Override
    public List<String> findRoleNames(String orgKey) {
        return List.copyOf(tenants.topLevelGroups(organization(orgKey)).keySet());
    }

    private OrganizationScope organization(String orgKey) {
        return tenants.organization(orgKey).orElseThrow(() -> new IdentityProviderUnavailableException(
                "No organization '" + orgKey + "' in realm '" + client.organizationRealm().orElse("") + "'"));
    }

    private static String groupsPath(OrganizationScope org) {
        return org.organizationPath() + "/groups";
    }
}

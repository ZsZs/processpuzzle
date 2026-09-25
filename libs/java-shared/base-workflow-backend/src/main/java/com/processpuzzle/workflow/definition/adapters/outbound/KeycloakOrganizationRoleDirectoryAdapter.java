package com.processpuzzle.workflow.definition.adapters.outbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.workflow.definition.usecases.outbound.RoleDirectoryPort;
import org.springframework.http.HttpMethod;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
 * <p>A role is a <em>top-level</em> group named after {@code RoleDefinition.id}; nested groups are
 * left to whoever made them. Same contract as {@link KeycloakRoleDirectoryAdapter} otherwise:
 * list-then-PUT-or-POST with the create's 409 tolerated, and an absent group on delete is success.
 *
 * <p>An {@code orgKey} no organization carries raises {@link IdentityProviderUnavailableException},
 * which {@code SyncRoleDirectory} already treats as "retry at the next reconcile" — the organization
 * may simply not have been provisioned yet.
 */
public class KeycloakOrganizationRoleDirectoryAdapter implements RoleDirectoryPort {

    private static final int CONFLICT = 409;

    /** Organizations have a handful of role groups; one page that no tenant will fill. */
    private static final int GROUP_PAGE = 500;

    private final KeycloakAdminClient client;
    private final String realm;

    public KeycloakOrganizationRoleDirectoryAdapter(KeycloakAdminClient client, String realm) {
        this.client = client;
        this.realm = realm;
    }

    @Override
    public void upsertRole(String orgKey, String roleName, String description) {
        String org = organizationId(orgKey);
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("name", roleName);
        representation.put("description", description);

        Optional<String> existing = groupId(org, roleName);
        if (existing.isPresent()) {
            client.exchange(HttpMethod.PUT, groupsPath(org) + "/" + existing.get(), representation, null);
        } else {
            client.exchangeTolerating(HttpMethod.POST, groupsPath(org), representation, CONFLICT);
        }
    }

    @Override
    public void deleteRole(String orgKey, String roleName) {
        String org = organizationId(orgKey);
        groupId(org, roleName).ifPresent(id ->
                client.exchange(HttpMethod.DELETE, groupsPath(org) + "/" + id, null, null));
    }

    @Override
    public List<String> findRoleNames(String orgKey) {
        return topLevelGroups(organizationId(orgKey)).stream()
                .map(group -> group.get("name"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private Optional<String> groupId(String org, String roleName) {
        return topLevelGroups(org).stream()
                .filter(group -> roleName.equals(String.valueOf(group.get("name"))))
                .map(group -> String.valueOf(group.get("id")))
                .findFirst();
    }

    /** {@code path} is relative to the organization, so a role group's is {@code /<name>}. */
    private List<Map<String, Object>> topLevelGroups(String org) {
        return client.getList(groupsPath(org) + "?first=0&max=" + GROUP_PAGE).stream()
                .filter(group -> ("/" + group.get("name")).equals(String.valueOf(group.get("path"))))
                .toList();
    }

    /** By alias, compared exactly: the search also matches names, and a look-alike must never answer. */
    private String organizationId(String orgKey) {
        String encoded = URLEncoder.encode(orgKey, StandardCharsets.UTF_8);
        return client.getList("/admin/realms/" + realm + "/organizations?exact=true&search=" + encoded).stream()
                .filter(org -> orgKey.equals(String.valueOf(org.get("alias"))))
                .map(org -> String.valueOf(org.get("id")))
                .findFirst()
                .orElseThrow(() -> new IdentityProviderUnavailableException(
                        "No organization '" + orgKey + "' in realm '" + realm + "'"));
    }

    private String groupsPath(String org) {
        return "/admin/realms/" + realm + "/organizations/" + org + "/groups";
    }
}

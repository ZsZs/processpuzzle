package com.processpuzzle.workflow.definition.adapters.outbound;

import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.TenantScope.RealmScope;
import com.processpuzzle.workflow.definition.usecases.outbound.RoleDirectoryPort;
import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Projects the role catalog into Keycloak realm roles, over core's {@link KeycloakAdminClient}.
 *
 * <p>Reuses that client rather than opening its own conversation — one token cache, one copy of
 * {@code keycloak.admin.*}; see its Javadoc for why the coupling is declared rather than avoided.
 * base-workflow already names {@code core} in its {@code allowedDependencies}, so this adds no
 * module edge.
 *
 * <p>The realm is {@code orgKey} verbatim, by the platform's naming convention — see
 * {@link RoleDirectoryPort} for why this adapter applies the convention itself instead of asking a
 * tenant registry, and what a deployment that has one should do instead.
 *
 * <h2>Upsert is list-then-PUT-or-POST</h2>
 *
 * <p>Keycloak has no upsert for realm roles: {@code POST /roles} answers 409 for an existing name and
 * {@code PUT /roles/{name}} answers 404 for a missing one. So the branch is chosen from a read, and
 * the 409 on the create branch is tolerated anyway — two requests can create the same role at once,
 * and the loser of that race has still had its work done.
 *
 * <p><b>The read is the role list, not {@code GET /roles/{name}}.</b> {@code GET /roles} always
 * answers 200 and is the same read {@link #findRoleNames} makes; a tenant's realm holds tens of
 * roles, so one list is no dearer than a {@link KeycloakAdminClient#find} probe.
 *
 * <p>Deliberately not a {@code @Component}: {@code RoleDirectoryConfiguration} registers either this
 * or the no-op, depending on whether an admin secret is configured.
 */
public class KeycloakRoleDirectoryAdapter implements RoleDirectoryPort {

    private final KeycloakAdminClient client;

    public KeycloakRoleDirectoryAdapter(KeycloakAdminClient client) {
        this.client = client;
    }

    @Override
    public void upsertRole(String orgKey, String roleName, String description) {
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("name", roleName);
        representation.put("description", description);

        if (findRoleNames(orgKey).contains(roleName)) {
            client.exchange(HttpMethod.PUT, rolePath(orgKey, roleName), representation, null);
        } else {
            client.exchangeTolerating(HttpMethod.POST, rolesPath(orgKey), representation,
                    KeycloakAdminClient.HTTP_CONFLICT);
        }
    }

    @Override
    public void deleteRole(String orgKey, String roleName) {
        client.exchangeTolerating(HttpMethod.DELETE, rolePath(orgKey, roleName), null,
                KeycloakAdminClient.HTTP_NOT_FOUND);
    }

    @Override
    public List<String> findRoleNames(String orgKey) {
        return client.getList(rolesPath(orgKey)).stream()
                .map(role -> role.get("name"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private static String rolesPath(String orgKey) {
        return new RealmScope(orgKey).adminRealmPath() + "/roles";
    }

    private static String rolePath(String orgKey, String roleName) {
        return rolesPath(orgKey) + "/" + roleName;
    }
}

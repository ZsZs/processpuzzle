package com.processpuzzle.workflow.definition.adapters.outbound;

import com.processpuzzle.core.identity.KeycloakAdminClient;
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
 * <p><b>The read is the role list, not {@code GET /roles/{name}}.</b> Keycloak answers 404 for a role
 * that is not there, and {@link KeycloakAdminClient#exchange} raises any 4xx as an
 * {@link com.processpuzzle.core.identity.IdentityProviderUnavailableException} rather than returning
 * an empty {@code Optional} — so a single-role probe cannot express "absent", and would send every
 * create down the failure path instead. {@code GET /roles} always answers 200, and a tenant's realm
 * holds tens of roles, so listing them is the cheaper mistake to make.
 *
 * <p>Deliberately not a {@code @Component}: {@code RoleDirectoryConfiguration} registers either this
 * or the no-op, depending on whether an admin secret is configured.
 */
public class KeycloakRoleDirectoryAdapter implements RoleDirectoryPort {

    /** Keycloak's answer when a role with that name is already there — a retry, not a failure. */
    private static final int CONFLICT = 409;

    /** Keycloak's answer when the role is already gone — the requested end state, reached. */
    private static final int NOT_FOUND = 404;

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
            client.exchangeTolerating(HttpMethod.POST, rolesPath(orgKey), representation, CONFLICT);
        }
    }

    @Override
    public void deleteRole(String orgKey, String roleName) {
        client.exchangeTolerating(HttpMethod.DELETE, rolePath(orgKey, roleName), null, NOT_FOUND);
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
        return "/admin/realms/" + orgKey + "/roles";
    }

    private static String rolePath(String orgKey, String roleName) {
        return rolesPath(orgKey) + "/" + roleName;
    }
}

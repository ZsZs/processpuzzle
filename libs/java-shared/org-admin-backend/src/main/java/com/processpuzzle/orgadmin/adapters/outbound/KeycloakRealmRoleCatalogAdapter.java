package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.TenantScope.RealmScope;
import com.processpuzzle.orgadmin.usecases.outbound.RoleCatalogPort;
import org.springframework.http.HttpMethod;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.processpuzzle.orgadmin.adapters.outbound.KeycloakUserDirectoryAdapter.wrap;

/**
 * The role catalog of a tenant that has a realm of its own: the roles are realm roles, over core's
 * {@link KeycloakAdminClient}.
 *
 * <p>One read of the role list, then a create per missing name. The 409 from a concurrent creator is
 * tolerated: whoever lost the race still got the role they wanted.
 *
 * <p>Deliberately not a {@code @Component}: {@code UserDirectoryConfiguration} picks it.
 */
public class KeycloakRealmRoleCatalogAdapter implements RoleCatalogPort {

    private final KeycloakAdminClient client;

    public KeycloakRealmRoleCatalogAdapter(KeycloakAdminClient client) {
        this.client = client;
    }

    @Override
    public void ensureRoles(String realm, Collection<String> roleNames, String description) {
        String rolesPath = new RealmScope(realm).adminRealmPath() + "/roles";
        Set<String> existing = client.getList(rolesPath).stream()
                .map(role -> role.get("name"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toSet());
        for (String name : roleNames) {
            if (existing.contains(name)) {
                continue;
            }
            Map<String, Object> representation = new LinkedHashMap<>();
            representation.put("name", name);
            representation.put("description", description);
            client.exchangeTolerating(HttpMethod.POST, rolesPath, representation, KeycloakAdminClient.HTTP_CONFLICT);
        }
    }

    @Override
    public void createRole(String realm, String name, String description) {
        wrap(() -> client.exchange(HttpMethod.POST, rolesPath(realm), representation(name, description), null));
    }

    @Override
    public void updateDescription(String realm, String name, String description) {
        wrap(() -> client.exchange(HttpMethod.PUT, rolesPath(realm) + "/" + name, representation(name, description), null));
    }

    @Override
    public void deleteRole(String realm, String name) {
        wrap(() -> {
            client.exchangeTolerating(HttpMethod.DELETE, rolesPath(realm) + "/" + name, null,
                    KeycloakAdminClient.HTTP_NOT_FOUND);
            return null;
        });
    }

    @Override
    public boolean hasHolders(String realm, String name) {
        return !wrap(() -> client.getList(rolesPath(realm) + "/" + name + "/users?first=0&max=1")).isEmpty();
    }

    private static String rolesPath(String realm) {
        return new RealmScope(realm).adminRealmPath() + "/roles";
    }

    private static Map<String, Object> representation(String name, String description) {
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("name", name);
        representation.put("description", description);
        return representation;
    }
}

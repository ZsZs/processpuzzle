package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.TenantScope.RealmScope;
import com.processpuzzle.orgadmin.usecases.outbound.RoleProvisioningPort;
import org.springframework.http.HttpMethod;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates missing realm roles over core's {@link KeycloakAdminClient}.
 *
 * <p>One read of the role list, then a create per missing name. The 409 from a concurrent creator is
 * tolerated: whoever lost the race still got the role they wanted.
 *
 * <p>Deliberately not a {@code @Component}: {@code UserDirectoryConfiguration} picks it.
 */
public class KeycloakRoleProvisioningAdapter implements RoleProvisioningPort {

    private final KeycloakAdminClient client;

    public KeycloakRoleProvisioningAdapter(KeycloakAdminClient client) {
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
}

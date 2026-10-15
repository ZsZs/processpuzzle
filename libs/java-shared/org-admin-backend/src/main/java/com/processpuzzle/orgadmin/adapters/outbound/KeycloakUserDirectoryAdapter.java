package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.tenancy.TenantRoles;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.identity.TenantScope.RealmScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The tenant's user directory, over Keycloak's Admin REST API.
 *
 * <p>Reuses core's {@link KeycloakAdminClient} rather than opening its own conversation:
 * one token cache, one copy of {@code keycloak.admin.*}.
 *
 * <p><b>Roles are resolved by name on every call.</b> Keycloak's role-mapping endpoints want the
 * {@code {id, name}} pair, and caching the ids would mean a realm re-import silently breaks every
 * grant. The cost is one extra GET per role touched, which is the right trade for an operation a
 * human performs by hand.
 *
 * <p>Transport failures are re-thrown as this module's {@link DirectoryUnavailableException}, not
 * core's. Invitations additionally distinguish unconfirmed external work from retryable failures.
 *
 * <p>Deliberately not a {@code @Component}: {@code UserDirectoryConfiguration} registers either this
 * or the no-op, depending on whether an admin secret is configured.
 */
public class KeycloakUserDirectoryAdapter implements UserDirectoryPort {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakUserDirectoryAdapter.class);

    private final KeycloakAdminClient client;

    public KeycloakUserDirectoryAdapter(KeycloakAdminClient client) {
        this.client = client;
    }

    @Override
    public DirectoryPage findUsers(String realm, String search, int page, int size) {
        StringBuilder path = new StringBuilder(usersPath(realm))
                .append("?first=").append((long) page * size).append("&max=").append(size);
        if (search != null && !search.isBlank()) {
            path.append("&search=").append(URLEncoder.encode(search, StandardCharsets.UTF_8));
        }
        List<Map<String, Object>> found = wrap(() -> client.getList(path.toString()));
        List<DirectoryUser> users = new ArrayList<>(found.size());
        for (Map<String, Object> raw : found) {
            users.add(toUser(raw, rolesOf(realm, str(raw.get("id")))));
        }
        return new DirectoryPage(users, estimateTotal(users.size(), page, size), page, size);
    }

    @Override
    public Optional<DirectoryUser> findUser(String realm, String userId) {
        Optional<Map<String, Object>> raw = wrap(() ->
                client.exchange(HttpMethod.GET, usersPath(realm) + "/" + userId,
                        null, Map.class).map(KeycloakUserDirectoryAdapter::asMap));
        return raw.map(user -> toUser(user, rolesOf(realm, userId)));
    }

    @Override
    public DirectoryUser inviteUser(String realm, NewUser user, List<String> roles) {
        return new KeycloakInvitation(client).invite(new RealmScope(realm), realm, user, roles,
                userId -> grantRoles(realm, userId, roles),
                userId -> findUser(realm, userId).orElseThrow(() -> new DirectoryUnavailableException(
                        "Keycloak will not read back the invited account '" + userId + "'.")));
    }

    @Override
    public DirectoryUser updateUser(String realm, String userId, UserProfile profile) {
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("email", profile.email());
        representation.put("firstName", profile.firstName());
        representation.put("lastName", profile.lastName());
        if (profile.enabled() != null) {
            representation.put("enabled", profile.enabled());
        }
        wrap(() -> client.exchange(HttpMethod.PUT, usersPath(realm) + "/" + userId,
                representation, null));
        return findUser(realm, userId).orElseThrow(() -> new DirectoryUnavailableException(
                "User '" + userId + "' disappeared from realm '" + realm + "' during an update."));
    }

    @Override
    public void deleteUser(String realm, String userId) {
        wrap(() -> client.exchange(HttpMethod.DELETE,
                usersPath(realm) + "/" + userId, null, null));
    }

    @Override
    public List<DirectoryRole> findRoles(String realm) {
        return wrap(() -> client.getList(rolesPath(realm))).stream()
                .map(KeycloakUserDirectoryAdapter::toRole)
                .toList();
    }

    @Override
    public List<DirectoryRole> findUserRoles(String realm, String userId) {
        return wrap(() -> client.getList(roleMappingPath(realm, userId))).stream()
                .map(KeycloakUserDirectoryAdapter::toRole)
                .toList();
    }

    @Override
    public List<DirectoryRole> replaceRoles(String realm, String userId, List<String> roles) {
        Set<String> wanted = Set.copyOf(roles == null ? List.of() : roles);
        Set<String> held = findUserRoles(realm, userId).stream()
                .map(DirectoryRole::name)
                .collect(Collectors.toUnmodifiableSet());

        // Revoke before granting: a payload that both drops and adds roles must never leave the user
        // momentarily holding the union, which is the wider of the two states.
        revokeRoles(realm, userId, held.stream().filter(role -> !wanted.contains(role)).toList());
        grantRoles(realm, userId, wanted.stream().filter(role -> !held.contains(role)).toList());
        return findUserRoles(realm, userId);
    }

    // --- helpers -------------------------------------------------------------------------

    private void grantRoles(String realm, String userId, List<String> roles) {
        List<Map<String, Object>> mappings = roleMappings(realm, roles);
        if (!mappings.isEmpty()) {
            wrap(() -> client.exchange(HttpMethod.POST,
                    roleMappingPath(realm, userId), mappings, null));
        }
    }

    private void revokeRoles(String realm, String userId, List<String> roles) {
        List<Map<String, Object>> mappings = roleMappings(realm, roles);
        if (!mappings.isEmpty()) {
            wrap(() -> client.exchange(HttpMethod.DELETE,
                    roleMappingPath(realm, userId), mappings, null));
        }
    }

    /**
     * Resolves role names to the {@code {id, name}} pairs Keycloak's role-mapping endpoints require.
     * A name the realm does not know is skipped with a warning rather than failing: the use case has
     * already refused unknown names, so reaching this branch means the realm changed underneath the
     * request, and dropping one role is better than abandoning the rest of the assignment.
     */
    private List<Map<String, Object>> roleMappings(String realm, List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> mappings = new ArrayList<>(roles.size());
        for (String role : roles) {
            wrap(() -> client.find(rolesPath(realm) + "/" + role, Map.class))
                    .ifPresentOrElse(
                            found -> mappings.add(Map.of("id", found.get("id"), "name", found.get("name"))),
                            () -> LOG.warn("Realm '{}' no longer declares role '{}'; skipped.", realm, role));
        }
        return mappings;
    }

    private static String usersPath(String realm) {
        return new RealmScope(realm).adminRealmPath() + "/users";
    }

    private static String rolesPath(String realm) {
        return new RealmScope(realm).adminRealmPath() + "/roles";
    }

    private static String roleMappingPath(String realm, String userId) {
        return usersPath(realm) + "/" + userId + "/role-mappings/realm";
    }

    private List<String> rolesOf(String realm, String userId) {
        return findUserRoles(realm, userId).stream().map(DirectoryRole::name).toList();
    }

    /**
     * Keycloak's user search returns no total, and a separate {@code /users/count} call per page is a
     * round trip for a number the UI only uses to draw a pager. So the total is the offset plus what
     * came back, and one more when the page came back full — enough for "next page" to be offered
     * exactly when there is one, and honest about being an estimate (see {@code DirectoryPage}).
     */
    static long estimateTotal(int returned, int page, int size) {
        long consumed = (long) page * size + returned;
        return returned == size ? consumed + 1 : consumed;
    }

    static DirectoryUser toUser(Map<String, Object> raw, List<String> roles) {
        Long createdTimestamp = raw.get("createdTimestamp") instanceof Number number
                ? number.longValue() : null;
        return new DirectoryUser(
                str(raw.get("id")),
                str(raw.get("username")),
                str(raw.get("email")),
                str(raw.get("firstName")),
                str(raw.get("lastName")),
                Boolean.TRUE.equals(raw.get("enabled")),
                Boolean.TRUE.equals(raw.get("emailVerified")),
                createdTimestamp == null ? null : Instant.ofEpochMilli(createdTimestamp),
                roles);
    }

    private static DirectoryRole toRole(Map<String, Object> raw) {
        String name = str(raw.get("name"));
        return new DirectoryRole(name, str(raw.get("description")), TenantRoles.ALL.contains(name));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        return (Map<String, Object>) raw;
    }

    static String str(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    /**
     * Re-throws core's transport failure as this module's, so this module's advice — which
     * is scoped to this module's controllers — is the one that answers it. Shared with
     * {@link KeycloakOrganizationUserDirectoryAdapter}.
     */
    static <T> T wrap(Supplier<T> call) {
        try {
            return call.get();
        } catch (IdentityProviderUnavailableException ex) {
            throw new DirectoryUnavailableException(ex.getMessage(), ex);
        }
    }
}

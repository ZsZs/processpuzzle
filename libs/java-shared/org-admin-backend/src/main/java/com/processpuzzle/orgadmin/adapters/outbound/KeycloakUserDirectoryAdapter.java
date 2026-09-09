package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.tenancy.TenantRoles;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
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
 * <p>Reuses {@code platform-admin}'s {@link KeycloakAdminClient} rather than opening its own
 * conversation — one token cache, one copy of {@code keycloak.admin.*}. See that class for why the
 * coupling is declared rather than avoided.
 *
 * <p><b>Roles are resolved by name on every call.</b> Keycloak's role-mapping endpoints want the
 * {@code {id, name}} pair, and caching the ids would mean a realm re-import silently breaks every
 * grant. The cost is one extra GET per role touched, which is the right trade for an operation a
 * human performs by hand.
 *
 * <p>Transport failures are re-thrown as this module's {@link DirectoryUnavailableException}, not
 * platform-admin's. Both map to 503, but each module's advice is scoped to its own controllers, so a
 * platform-admin exception raised from an org-admin endpoint would fall through to core's catch-all
 * as a 500 instead.
 *
 * <p>Deliberately not a {@code @Component}: {@code UserDirectoryConfiguration} registers either this
 * or the no-op, depending on whether an admin secret is configured.
 */
public class KeycloakUserDirectoryAdapter implements UserDirectoryPort {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakUserDirectoryAdapter.class);

    /** Forces the invitee to set their own password on first login. */
    private static final String UPDATE_PASSWORD = "UPDATE_PASSWORD";

    /** The two roles created with every realm, which ProcessPuzzle itself interprets. */
    private static final Set<String> PLATFORM_ROLES =
            Set.of(TenantRoles.ORG_ADMIN, TenantRoles.ORG_MEMBER);

    private final KeycloakAdminClient client;

    public KeycloakUserDirectoryAdapter(KeycloakAdminClient client) {
        this.client = client;
    }

    @Override
    public DirectoryPage findUsers(String realm, String search, int page, int size) {
        StringBuilder path = new StringBuilder("/admin/realms/").append(realm)
                .append("/users?first=").append((long) page * size).append("&max=").append(size);
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
                client.exchange(HttpMethod.GET, "/admin/realms/" + realm + "/users/" + userId,
                        null, Map.class).map(KeycloakUserDirectoryAdapter::asMap));
        return raw.map(user -> toUser(user, rolesOf(realm, userId)));
    }

    @Override
    public DirectoryUser inviteUser(String realm, NewUser user, List<String> roles) {
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("username", user.username());
        representation.put("email", user.email());
        representation.put("firstName", user.firstName());
        representation.put("lastName", user.lastName());
        representation.put("enabled", true);
        representation.put("emailVerified", false);
        representation.put("requiredActions", List.of(UPDATE_PASSWORD));

        String userId = createUser(realm, user, representation);
        grantRoles(realm, userId, roles);
        return findUser(realm, userId).orElseThrow(() -> new DirectoryUnavailableException(
                "Keycloak reported user '" + userId + "' created in realm '" + realm
                        + "' but will not read it back."));
    }

    /**
     * Keycloak answers 409 for a duplicate username or email. Distinguished from a transport failure
     * because the contract declares 409 for it, and because a 503 would tell the administrator to
     * retry something that can never succeed.
     */
    private String createUser(String realm, NewUser user, Map<String, Object> representation) {
        try {
            return client.createAndReturnId("/admin/realms/" + realm + "/users", representation)
                    .orElseThrow(() -> new DirectoryUnavailableException(
                            "Keycloak created a user in realm '" + realm + "' but returned no id."));
        } catch (IdentityProviderUnavailableException ex) {
            if (String.valueOf(ex.getCause()).contains("409")) {
                throw new UserAlreadyExistsException(realm, user.username());
            }
            throw new DirectoryUnavailableException(ex.getMessage(), ex);
        }
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
        wrap(() -> client.exchange(HttpMethod.PUT, "/admin/realms/" + realm + "/users/" + userId,
                representation, null));
        return findUser(realm, userId).orElseThrow(() -> new DirectoryUnavailableException(
                "User '" + userId + "' disappeared from realm '" + realm + "' during an update."));
    }

    @Override
    public void deleteUser(String realm, String userId) {
        wrap(() -> client.exchange(HttpMethod.DELETE,
                "/admin/realms/" + realm + "/users/" + userId, null, null));
    }

    @Override
    public List<DirectoryRole> findRoles(String realm) {
        return wrap(() -> client.getList("/admin/realms/" + realm + "/roles")).stream()
                .map(KeycloakUserDirectoryAdapter::toRole)
                .toList();
    }

    @Override
    public List<DirectoryRole> findUserRoles(String realm, String userId) {
        return wrap(() -> client.getList(
                "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm")).stream()
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
                    "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm",
                    mappings, null));
        }
    }

    private void revokeRoles(String realm, String userId, List<String> roles) {
        List<Map<String, Object>> mappings = roleMappings(realm, roles);
        if (!mappings.isEmpty()) {
            wrap(() -> client.exchange(HttpMethod.DELETE,
                    "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm",
                    mappings, null));
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
            wrap(() -> client.exchange(HttpMethod.GET, "/admin/realms/" + realm + "/roles/" + role,
                    null, Map.class))
                    .ifPresentOrElse(
                            found -> mappings.add(Map.of("id", found.get("id"), "name", found.get("name"))),
                            () -> LOG.warn("Realm '{}' no longer declares role '{}'; skipped.", realm, role));
        }
        return mappings;
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
    private static long estimateTotal(int returned, int page, int size) {
        long consumed = (long) page * size + returned;
        return returned == size ? consumed + 1 : consumed;
    }

    private static DirectoryUser toUser(Map<String, Object> raw, List<String> roles) {
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
        return new DirectoryRole(name, str(raw.get("description")), PLATFORM_ROLES.contains(name));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        return (Map<String, Object>) raw;
    }

    private static String str(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    /**
     * Re-throws platform-admin's transport failure as this module's, so this module's advice — which
     * is scoped to this module's controllers — is the one that answers it.
     */
    private static <T> T wrap(Supplier<T> call) {
        try {
            return call.get();
        } catch (IdentityProviderUnavailableException ex) {
            throw new DirectoryUnavailableException(ex.getMessage(), ex);
        }
    }
}

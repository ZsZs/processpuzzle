package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.tenancy.TenantRoles;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownOrganizationException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserNotFoundException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.processpuzzle.orgadmin.adapters.outbound.KeycloakUserDirectoryAdapter.estimateTotal;
import static com.processpuzzle.orgadmin.adapters.outbound.KeycloakUserDirectoryAdapter.str;
import static com.processpuzzle.orgadmin.adapters.outbound.KeycloakUserDirectoryAdapter.toUser;

/**
 * The tenant's user directory when every tenant is a Keycloak <b>Organization</b> inside one shared
 * realm — the Custom stack's model — rather than a realm of its own.
 *
 * <p>The {@code directory} argument of every port method is what {@code TenantRealmResolver}
 * returned, which by the platform's convention is the {@code orgKey}. Here it names the
 * organization by alias; the realm is fixed, {@code keycloak.admin.organization-realm}. An alias no
 * organization carries is a 404, not a 503: the tenant does not exist, and retrying will not help.
 *
 * <h2>Membership is the tenant boundary</h2>
 *
 * <p>The realm holds every customer's users, so realm-wide endpoints are never used to <em>find</em>
 * anyone: listing goes through the organization's members, and every per-user operation first asks
 * Keycloak whether that user is a member of <em>this</em> organization. A user id from another
 * tenant is therefore "not found" — never readable, editable or deletable — however it was obtained.
 *
 * <h2>Two kinds of role</h2>
 *
 * <ul>
 *   <li><b>{@code org-admin} and {@code org-member}</b> stay realm roles. They are created once for
 *       the realm, say what a person may do rather than where, and the seed job already grants them
 *       that way. The caveat is theirs to carry: a user who belonged to two organizations would hold
 *       {@code org-admin} in both. Nothing creates such a user today.
 *   <li><b>Every other role is the tenant's own</b> — a workflow role, projected by base-workflow —
 *       and is an organization <em>group</em>, which Keycloak scopes to the organization (26.6+): two
 *       customers' {@code reviewer} are two groups, and Keycloak refuses to put a non-member into
 *       either. Only top-level groups are roles; a nested group is structure, not a role.
 * </ul>
 *
 * <p>Deliberately not a {@code @Component}; {@code UserDirectoryConfiguration} chooses it.
 */
public class KeycloakOrganizationUserDirectoryAdapter implements UserDirectoryPort {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakOrganizationUserDirectoryAdapter.class);

    private static final String UPDATE_PASSWORD = "UPDATE_PASSWORD";

    private static final Set<String> PLATFORM_ROLES = Set.of(TenantRoles.ORG_ADMIN, TenantRoles.ORG_MEMBER);

    /** Organizations have a handful of role groups; one page that no tenant will fill. */
    private static final int GROUP_PAGE = 500;

    private final KeycloakAdminClient client;
    private final String realm;

    public KeycloakOrganizationUserDirectoryAdapter(KeycloakAdminClient client, String realm) {
        this.client = client;
        this.realm = realm;
    }

    @Override
    public DirectoryPage findUsers(String directory, String search, int page, int size) {
        String org = organizationId(directory);
        StringBuilder path = new StringBuilder(orgPath(org)).append("/members?first=")
                .append((long) page * size).append("&max=").append(size);
        if (search != null && !search.isBlank()) {
            path.append("&search=").append(URLEncoder.encode(search, StandardCharsets.UTF_8));
        }
        List<Map<String, Object>> found = wrap(() -> client.getList(path.toString()));
        List<DirectoryUser> users = new ArrayList<>(found.size());
        for (Map<String, Object> raw : found) {
            users.add(toUser(raw, roleNames(org, str(raw.get("id")))));
        }
        return new DirectoryPage(users, estimateTotal(users.size(), page, size), page, size);
    }

    @Override
    public Optional<DirectoryUser> findUser(String directory, String userId) {
        String org = organizationId(directory);
        return member(org, userId).map(raw -> toUser(raw, roleNames(org, userId)));
    }

    @Override
    public DirectoryUser inviteUser(String directory, NewUser user, List<String> roles) {
        String org = organizationId(directory);
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("username", user.username());
        representation.put("email", user.email());
        representation.put("firstName", user.firstName());
        representation.put("lastName", user.lastName());
        representation.put("enabled", true);
        representation.put("emailVerified", false);
        representation.put("requiredActions", List.of(UPDATE_PASSWORD));

        String userId = createUser(directory, user, representation);
        try {
            // The body is the id as a JSON string, quotes included. Spring writes a String body
            // verbatim, and Keycloak refuses a bare id with 400.
            wrap(() -> client.exchange(HttpMethod.POST, orgPath(org) + "/members", "\"" + userId + "\"", null));
        } catch (DirectoryUnavailableException ex) {
            // Undone rather than left behind: a realm user who belongs to no organization is invisible
            // to every tenant's administrator, so nobody could ever find it to finish or remove it.
            deleteAccountQuietly(userId);
            throw ex;
        }
        applyRoles(org, userId, Set.of(), Set.copyOf(roles == null ? List.of() : roles));
        return findUser(directory, userId).orElseThrow(() -> new DirectoryUnavailableException(
                "Keycloak reported user '" + userId + "' added to organization '" + directory
                        + "' but will not read it back."));
    }

    /**
     * 409 is a duplicate username or email <em>in the realm</em> — possibly another tenant's user, so
     * the message names only this tenant's attempt, never whose account it collided with.
     */
    private String createUser(String directory, NewUser user, Map<String, Object> representation) {
        try {
            return client.createAndReturnId(realmPath() + "/users", representation)
                    .orElseThrow(() -> new DirectoryUnavailableException(
                            "Keycloak created a user for organization '" + directory + "' but returned no id."));
        } catch (IdentityProviderUnavailableException ex) {
            if (ex.getCause() instanceof HttpClientErrorException.Conflict) {
                throw new UserAlreadyExistsException(directory, user.username());
            }
            throw new DirectoryUnavailableException(ex.getMessage(), ex);
        }
    }

    @Override
    public DirectoryUser updateUser(String directory, String userId, UserProfile profile) {
        String org = organizationId(directory);
        requireMember(org, directory, userId);
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("email", profile.email());
        representation.put("firstName", profile.firstName());
        representation.put("lastName", profile.lastName());
        if (profile.enabled() != null) {
            representation.put("enabled", profile.enabled());
        }
        wrap(() -> client.exchange(HttpMethod.PUT, realmPath() + "/users/" + userId, representation, null));
        return findUser(directory, userId).orElseThrow(() -> new DirectoryUnavailableException(
                "User '" + userId + "' left organization '" + directory + "' during an update."));
    }

    /**
     * Removes the user from this organization, and deletes the account only when no other
     * organization still has them — a tenant administrator may end a membership in their own tenant,
     * never an account another tenant depends on.
     */
    @Override
    public void deleteUser(String directory, String userId) {
        String org = organizationId(directory);
        requireMember(org, directory, userId);
        wrap(() -> client.exchange(HttpMethod.DELETE, orgPath(org) + "/members/" + userId, null, null));
        boolean elsewhere = !wrap(() -> client.getList(
                realmPath() + "/organizations/members/" + userId + "/organizations")).isEmpty();
        if (!elsewhere) {
            wrap(() -> client.exchange(HttpMethod.DELETE, realmPath() + "/users/" + userId, null, null));
        }
    }

    @Override
    public List<DirectoryRole> findRoles(String directory) {
        String org = organizationId(directory);
        return Stream.concat(platformRoles().stream(), roleGroups(org).stream().map(
                        group -> new DirectoryRole(str(group.get("name")), str(group.get("description")), false)))
                .toList();
    }

    @Override
    public List<DirectoryRole> findUserRoles(String directory, String userId) {
        String org = organizationId(directory);
        requireMember(org, directory, userId);
        return userRoles(org, userId);
    }

    @Override
    public List<DirectoryRole> replaceRoles(String directory, String userId, List<String> roles) {
        String org = organizationId(directory);
        requireMember(org, directory, userId);
        Set<String> held = userRoles(org, userId).stream().map(DirectoryRole::name)
                .collect(Collectors.toUnmodifiableSet());
        applyRoles(org, userId, held, Set.copyOf(roles == null ? List.of() : roles));
        return userRoles(org, userId);
    }

    // --- roles ---------------------------------------------------------------------------

    /**
     * Moves the user from {@code held} to {@code wanted}. Revokes before granting, for the reason the
     * realm adapter gives: a user must never momentarily hold the union of the two states.
     */
    private void applyRoles(String org, String userId, Set<String> held, Set<String> wanted) {
        List<String> revoke = held.stream().filter(role -> !wanted.contains(role)).toList();
        List<String> grant = wanted.stream().filter(role -> !held.contains(role)).toList();

        List<Map<String, Object>> platformRevoke = platformMappings(revoke);
        if (!platformRevoke.isEmpty()) {
            wrap(() -> client.exchange(HttpMethod.DELETE, realmRoleMappingPath(userId), platformRevoke, null));
        }
        Map<String, String> groupIds = roleGroups(org).stream()
                .collect(Collectors.toMap(group -> str(group.get("name")), group -> str(group.get("id")), (a, b) -> a));
        for (String role : revoke) {
            if (!PLATFORM_ROLES.contains(role) && groupIds.containsKey(role)) {
                wrap(() -> client.exchange(HttpMethod.DELETE, groupMemberPath(org, groupIds.get(role), userId), null, null));
            }
        }

        List<Map<String, Object>> platformGrant = platformMappings(grant);
        if (!platformGrant.isEmpty()) {
            wrap(() -> client.exchange(HttpMethod.POST, realmRoleMappingPath(userId), platformGrant, null));
        }
        for (String role : grant) {
            if (PLATFORM_ROLES.contains(role)) {
                continue;
            }
            String groupId = groupIds.get(role);
            if (groupId == null) {
                // As in the realm adapter: the use case refused unknown names already, so the group
                // vanished underneath this request. Dropping one role beats abandoning the rest.
                LOG.warn("Organization '{}' no longer has role group '{}'; skipped.", org, role);
                continue;
            }
            wrap(() -> client.exchange(HttpMethod.PUT, groupMemberPath(org, groupId, userId), null, null));
        }
    }

    private List<DirectoryRole> userRoles(String org, String userId) {
        List<DirectoryRole> platform = wrap(() -> client.getList(realmRoleMappingPath(userId))).stream()
                .filter(role -> PLATFORM_ROLES.contains(str(role.get("name"))))
                .map(role -> new DirectoryRole(str(role.get("name")), str(role.get("description")), true))
                .toList();
        List<DirectoryRole> own = wrap(() -> client.getList(orgPath(org) + "/members/" + userId + "/groups")).stream()
                .filter(KeycloakOrganizationUserDirectoryAdapter::isTopLevel)
                .map(group -> new DirectoryRole(str(group.get("name")), str(group.get("description")), false))
                .toList();
        return Stream.concat(platform.stream(), own.stream()).toList();
    }

    private List<String> roleNames(String org, String userId) {
        return userRoles(org, userId).stream().map(DirectoryRole::name).toList();
    }

    private List<DirectoryRole> platformRoles() {
        return wrap(() -> client.getList(realmPath() + "/roles")).stream()
                .filter(role -> PLATFORM_ROLES.contains(str(role.get("name"))))
                .map(role -> new DirectoryRole(str(role.get("name")), str(role.get("description")), true))
                .toList();
    }

    /** The {@code {id, name}} pairs Keycloak's role-mapping endpoints want, for the platform roles among {@code roles}. */
    private List<Map<String, Object>> platformMappings(List<String> roles) {
        if (roles.stream().noneMatch(PLATFORM_ROLES::contains)) {
            return List.of();
        }
        return wrap(() -> client.getList(realmPath() + "/roles")).stream()
                .filter(role -> PLATFORM_ROLES.contains(str(role.get("name"))) && roles.contains(str(role.get("name"))))
                .map(role -> Map.of("id", role.get("id"), "name", role.get("name")))
                .toList();
    }

    private List<Map<String, Object>> roleGroups(String org) {
        return wrap(() -> client.getList(orgPath(org) + "/groups?first=0&max=" + GROUP_PAGE)).stream()
                .filter(KeycloakOrganizationUserDirectoryAdapter::isTopLevel)
                .toList();
    }

    /** {@code path} is relative to the organization: a role group is {@code /reviewer}. */
    private static boolean isTopLevel(Map<String, Object> group) {
        return ("/" + str(group.get("name"))).equals(str(group.get("path")));
    }

    // --- organization and membership -----------------------------------------------------

    /**
     * The organization's id from its alias. {@code exact=true} and an alias comparison both: the
     * search also matches names, and a tenant must never be resolved to a look-alike.
     */
    private String organizationId(String alias) {
        String encoded = URLEncoder.encode(alias, StandardCharsets.UTF_8);
        return wrap(() -> client.getList(realmPath() + "/organizations?exact=true&search=" + encoded)).stream()
                .filter(org -> alias.equals(str(org.get("alias"))))
                .map(org -> str(org.get("id")))
                .findFirst()
                .orElseThrow(() -> new UnknownOrganizationException(alias));
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> member(String org, String userId) {
        try {
            return client.exchange(HttpMethod.GET, orgPath(org) + "/members/" + userId, null, Map.class)
                    .map(raw -> (Map<String, Object>) raw);
        } catch (IdentityProviderUnavailableException ex) {
            if (ex.getCause() instanceof HttpClientErrorException.NotFound) {
                return Optional.empty();
            }
            throw new DirectoryUnavailableException(ex.getMessage(), ex);
        }
    }

    private void requireMember(String org, String directory, String userId) {
        if (member(org, userId).isEmpty()) {
            throw new UserNotFoundException(directory, userId);
        }
    }

    private void deleteAccountQuietly(String userId) {
        try {
            client.exchange(HttpMethod.DELETE, realmPath() + "/users/" + userId, null, null);
        } catch (RuntimeException cleanup) {
            LOG.error("User '{}' was created in '{}' but could not be added to an organization or removed again; "
                    + "delete it by hand.", userId, realm, cleanup);
        }
    }

    // --- paths and errors ----------------------------------------------------------------

    private String realmPath() {
        return "/admin/realms/" + realm;
    }

    private String orgPath(String org) {
        return realmPath() + "/organizations/" + org;
    }

    private String realmRoleMappingPath(String userId) {
        return realmPath() + "/users/" + userId + "/role-mappings/realm";
    }

    private String groupMemberPath(String org, String groupId, String userId) {
        return orgPath(org) + "/groups/" + groupId + "/members/" + userId;
    }

    private static <T> T wrap(Supplier<T> call) {
        try {
            return call.get();
        } catch (IdentityProviderUnavailableException ex) {
            throw new DirectoryUnavailableException(ex.getMessage(), ex);
        }
    }
}

package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.KeycloakTenantDirectory;
import com.processpuzzle.core.identity.TenantScope.OrganizationScope;
import com.processpuzzle.core.tenancy.TenantRoles;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownOrganizationException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserNotFoundException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.processpuzzle.orgadmin.adapters.outbound.KeycloakUserDirectoryAdapter.estimateTotal;
import static com.processpuzzle.orgadmin.adapters.outbound.KeycloakUserDirectoryAdapter.str;
import static com.processpuzzle.orgadmin.adapters.outbound.KeycloakUserDirectoryAdapter.toUser;
import static com.processpuzzle.orgadmin.adapters.outbound.KeycloakUserDirectoryAdapter.wrap;

/**
 * The tenant's user directory when every tenant is a Keycloak <b>Organization</b> inside one shared
 * realm — the Custom stack's model — rather than a realm of its own.
 *
 * <p>The {@code directory} argument of every port method is what {@code TenantRealmResolver}
 * returned, which by the platform's convention is the {@code orgKey}. Here it names the
 * organization by alias; the realm is fixed, {@code keycloak.admin.organization-realm}. An alias no
 * organization carries is a 404, not a 503: the tenant does not exist, and retrying will not help.
 * Finding the organization, its role groups and its members is core's
 * {@code KeycloakTenantDirectory}; what they mean to user management is this class's.
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

    private final KeycloakAdminClient client;
    private final KeycloakTenantDirectory tenants;

    public KeycloakOrganizationUserDirectoryAdapter(KeycloakAdminClient client, KeycloakTenantDirectory tenants) {
        this.client = client;
        this.tenants = tenants;
    }

    @Override
    public DirectoryPage findUsers(String directory, String search, int page, int size) {
        OrganizationScope org = organization(directory);
        StringBuilder path = new StringBuilder(org.organizationPath()).append("/members?first=")
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
        OrganizationScope org = organization(directory);
        return member(org, userId).map(raw -> toUser(raw, roleNames(org, userId)));
    }

    @Override
    public DirectoryUser inviteUser(String directory, NewUser user, List<String> roles) {
        OrganizationScope org = organization(directory);
        return new KeycloakInvitation(client).invite(org, directory, user, roles, userId -> {
            // Spring writes String bodies verbatim; Keycloak requires a JSON string.
            wrap(() -> client.exchange(HttpMethod.POST, org.organizationPath() + "/members", "\"" + userId + "\"", null));
            applyRoles(org, userId, Set.of(), Set.copyOf(roles == null ? List.of() : roles));
        }, userId -> member(org, userId).map(raw -> toUser(raw, roleNames(org, userId)))
                .orElseThrow(() -> new DirectoryUnavailableException(
                        "Keycloak will not read back the invited member '" + userId + "'.")));
    }

    @Override
    public DirectoryUser updateUser(String directory, String userId, UserProfile profile) {
        OrganizationScope org = organization(directory);
        requireMember(org, userId);
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("email", profile.email());
        representation.put("firstName", profile.firstName());
        representation.put("lastName", profile.lastName());
        if (profile.enabled() != null) {
            representation.put("enabled", profile.enabled());
        }
        wrap(() -> client.exchange(HttpMethod.PUT, userPath(org, userId), representation, null));
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
        OrganizationScope org = organization(directory);
        requireMember(org, userId);
        wrap(() -> client.exchange(HttpMethod.DELETE, memberPath(org, userId), null, null));
        boolean elsewhere = !wrap(() -> client.getList(
                org.adminRealmPath() + "/organizations/members/" + userId + "/organizations")).isEmpty();
        if (!elsewhere) {
            wrap(() -> client.exchange(HttpMethod.DELETE, userPath(org, userId), null, null));
        }
    }

    @Override
    public List<DirectoryRole> findRoles(String directory) {
        OrganizationScope org = organization(directory);
        List<Map<String, Object>> groups = wrap(() -> tenants.topLevelGroupRepresentations(org));
        return Stream.concat(platformRoles(org).stream(), groups.stream().map(
                        group -> new DirectoryRole(str(group.get("name")), str(group.get("description")), false)))
                .toList();
    }

    @Override
    public List<DirectoryRole> findUserRoles(String directory, String userId) {
        OrganizationScope org = organization(directory);
        requireMember(org, userId);
        return userRoles(org, userId);
    }

    @Override
    public List<DirectoryRole> replaceRoles(String directory, String userId, List<String> roles) {
        OrganizationScope org = organization(directory);
        requireMember(org, userId);
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
    private void applyRoles(OrganizationScope org, String userId, Set<String> held, Set<String> wanted) {
        List<String> revoke = held.stream().filter(role -> !wanted.contains(role)).toList();
        List<String> grant = wanted.stream().filter(role -> !held.contains(role)).toList();

        List<Map<String, Object>> platformRevoke = platformMappings(org, revoke);
        if (!platformRevoke.isEmpty()) {
            wrap(() -> client.exchange(HttpMethod.DELETE, realmRoleMappingPath(org, userId), platformRevoke, null));
        }
        Map<String, String> groupIds = wrap(() -> tenants.topLevelGroups(org));
        for (String role : revoke) {
            if (!TenantRoles.ALL.contains(role) && groupIds.containsKey(role)) {
                wrap(() -> client.exchange(HttpMethod.DELETE, groupMemberPath(org, groupIds.get(role), userId), null, null));
            }
        }

        List<Map<String, Object>> platformGrant = platformMappings(org, grant);
        if (!platformGrant.isEmpty()) {
            wrap(() -> client.exchange(HttpMethod.POST, realmRoleMappingPath(org, userId), platformGrant, null));
        }
        for (String role : grant) {
            if (TenantRoles.ALL.contains(role)) {
                continue;
            }
            String groupId = groupIds.get(role);
            if (groupId == null) {
                // As in the realm adapter: the use case refused unknown names already, so the group
                // vanished underneath this request. Dropping one role beats abandoning the rest.
                LOG.warn("Organization '{}' no longer has role group '{}'; skipped.", org.alias(), role);
                continue;
            }
            wrap(() -> client.exchange(HttpMethod.PUT, groupMemberPath(org, groupId, userId), null, null));
        }
    }

    private List<DirectoryRole> userRoles(OrganizationScope org, String userId) {
        List<DirectoryRole> platform = wrap(() -> client.getList(realmRoleMappingPath(org, userId))).stream()
                .filter(role -> TenantRoles.ALL.contains(str(role.get("name"))))
                .map(role -> new DirectoryRole(str(role.get("name")), str(role.get("description")), true))
                .toList();
        List<DirectoryRole> own = wrap(() -> client.getList(memberPath(org, userId) + "/groups"))
                .stream()
                .filter(KeycloakTenantDirectory::isTopLevel)
                .map(group -> new DirectoryRole(str(group.get("name")), str(group.get("description")), false))
                .toList();
        return Stream.concat(platform.stream(), own.stream()).toList();
    }

    private List<String> roleNames(OrganizationScope org, String userId) {
        return userRoles(org, userId).stream().map(DirectoryRole::name).toList();
    }

    private List<DirectoryRole> platformRoles(OrganizationScope org) {
        return wrap(() -> client.getList(org.adminRealmPath() + "/roles")).stream()
                .filter(role -> TenantRoles.ALL.contains(str(role.get("name"))))
                .map(role -> new DirectoryRole(str(role.get("name")), str(role.get("description")), true))
                .toList();
    }

    /** The {@code {id, name}} pairs Keycloak's role-mapping endpoints want, for the platform roles among {@code roles}. */
    private List<Map<String, Object>> platformMappings(OrganizationScope org, List<String> roles) {
        if (roles.stream().noneMatch(TenantRoles.ALL::contains)) {
            return List.of();
        }
        return wrap(() -> client.getList(org.adminRealmPath() + "/roles")).stream()
                .filter(role -> TenantRoles.ALL.contains(str(role.get("name"))) && roles.contains(str(role.get("name"))))
                .map(role -> Map.of("id", role.get("id"), "name", role.get("name")))
                .toList();
    }

    // --- organization and membership -----------------------------------------------------

    /** The organization behind the tenant's alias; one that no organization carries is a 404. */
    private OrganizationScope organization(String alias) {
        return wrap(() -> tenants.organization(alias)).orElseThrow(() -> new UnknownOrganizationException(alias));
    }

    private Optional<Map<String, Object>> member(OrganizationScope org, String userId) {
        return wrap(() -> tenants.findMember(org, userId));
    }

    private void requireMember(OrganizationScope org, String userId) {
        if (member(org, userId).isEmpty()) {
            throw new UserNotFoundException(org.alias(), userId);
        }
    }

    // --- paths ---------------------------------------------------------------------------

    private static String userPath(OrganizationScope org, String userId) {
        return org.adminRealmPath() + "/users/" + userId;
    }

    private static String memberPath(OrganizationScope org, String userId) {
        return org.organizationPath() + "/members/" + userId;
    }

    private static String realmRoleMappingPath(OrganizationScope org, String userId) {
        return userPath(org, userId) + "/role-mappings/realm";
    }

    private static String groupMemberPath(OrganizationScope org, String groupId, String userId) {
        return org.organizationPath() + "/groups/" + groupId + "/members/" + userId;
    }
}

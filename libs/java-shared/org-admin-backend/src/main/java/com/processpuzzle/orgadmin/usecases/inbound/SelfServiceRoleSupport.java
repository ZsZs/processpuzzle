package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.SelfServiceRolesDisabledException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * What {@link FindMyRoles} and {@link ReplaceMyRoles} share: the feature switch, the caller's identity
 * and the definition of "selectable".
 *
 * <p>The user id is always the token's subject and never a parameter, so the endpoint cannot be
 * pointed at anyone but the caller.
 *
 * <p><b>Selectable</b> is every realm role except the platform-managed ones ({@code org-admin},
 * {@code org-member}), {@code platform-admin}, and the identity provider's own built-ins. Anything
 * that grants administrative reach must stay out of a screen every member can use.
 */
@Component
class SelfServiceRoleSupport {

    private static final String PLATFORM_ADMIN = "platform-admin";
    private static final List<String> BUILT_IN_ROLES = List.of("offline_access", "uma_authorization");
    private static final String DEFAULT_ROLES_PREFIX = "default-roles-";

    private final SelfServiceRolesProperties properties;
    private final TenantRealmResolver realms;
    private final CurrentPrincipal principal;
    private final UserDirectoryPort directory;

    SelfServiceRoleSupport(SelfServiceRolesProperties properties, TenantRealmResolver realms,
                           CurrentPrincipal principal, UserDirectoryPort directory) {
        this.properties = properties;
        this.realms = realms;
        this.principal = principal;
        this.directory = directory;
    }

    /** Refuses when disabled, then returns the caller in their realm. */
    Caller caller(String orgKey) {
        if (!properties.isEnabled()) {
            throw new SelfServiceRolesDisabledException("Self-service roles are not enabled.");
        }
        String realm = realms.resolveForMember(orgKey);
        String userId = principal.subject().orElseThrow(() -> new OrganizationAccessDeniedException(orgKey));
        return new Caller(realm, userId);
    }

    List<DirectoryRole> selectable(String realm) {
        return directory.findRoles(realm).stream().filter(SelfServiceRoleSupport::isSelectable).toList();
    }

    static boolean isSelectable(DirectoryRole role) {
        String name = role.name();
        return !role.platformManaged()
                && !PLATFORM_ADMIN.equals(name)
                && !BUILT_IN_ROLES.contains(name)
                && !name.startsWith(DEFAULT_ROLES_PREFIX);
    }

    MyRoles read(Caller caller) {
        List<String> assigned = directory.findUserRoles(caller.realm(), caller.userId()).stream()
                .map(DirectoryRole::name).toList();
        return new MyRoles(assigned, selectable(caller.realm()));
    }

    record Caller(String realm, String userId) {
    }
}

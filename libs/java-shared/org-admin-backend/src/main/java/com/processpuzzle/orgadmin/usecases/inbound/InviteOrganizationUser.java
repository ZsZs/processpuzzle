package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Invites a user into the tenant.
 *
 * <p>{@code org-member} is added to whatever roles were requested, always. Role checks elsewhere in
 * the platform — nav visibility, workflow role assignment — match on it, so a user invited with only
 * a specialised role would be invisible to all of them. A caller that names it explicitly gets it
 * once: the set is deduplicated, and ordered so the requested roles keep their order in the response.
 */
@Service
public class InviteOrganizationUser {

    private final TenantRealmResolver realms;
    private final UserDirectoryPort directory;

    public InviteOrganizationUser(TenantRealmResolver realms, UserDirectoryPort directory) {
        this.realms = realms;
        this.directory = directory;
    }

    public DirectoryUser execute(String orgKey, UserDirectoryPort.NewUser user, List<String> roles) {
        String realm = realms.resolve(orgKey);
        return directory.inviteUser(realm, user, withMemberRole(roles));
    }

    private static List<String> withMemberRole(List<String> requested) {
        Set<String> effective = new LinkedHashSet<>();
        if (requested != null) {
            requested.stream().filter(role -> role != null && !role.isBlank()).forEach(effective::add);
        }
        effective.add(IdentityRealmPort.ORG_MEMBER_ROLE);
        return List.copyOf(effective);
    }
}

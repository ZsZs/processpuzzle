package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownRoleException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserNotFoundException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Makes a user hold exactly the given set of realm roles.
 *
 * <p>A full replacement rather than add/remove verbs: the role-assignment screen presents the realm's
 * roles as a checkbox set and saves the whole set, and two concurrent editors of that screen must not
 * silently merge into a union neither of them chose. The port computes the grants and revocations
 * from the difference.
 *
 * <p>Every name is checked against the realm's declared roles <em>before</em> anything is written, so
 * a payload with one typo in it changes nothing rather than applying the rest. Creating the missing
 * role instead would mint one that nothing in the platform ever matches — invisible until someone
 * wondered why a user's permissions had no effect.
 */
@Service
public class ReplaceOrganizationUserRoles {

    private final TenantRealmResolver realms;
    private final UserDirectoryPort directory;

    public ReplaceOrganizationUserRoles(TenantRealmResolver realms, UserDirectoryPort directory) {
        this.realms = realms;
        this.directory = directory;
    }

    public List<DirectoryRole> execute(String orgKey, String userId, List<String> roles) {
        String realm = realms.resolve(orgKey);
        if (directory.findUser(realm, userId).isEmpty()) {
            throw new UserNotFoundException(realm, userId);
        }

        List<String> requested = roles == null ? List.of() : roles;
        Set<String> declared = directory.findRoles(realm).stream()
                .map(DirectoryRole::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (String role : requested) {
            if (!declared.contains(role)) {
                throw new UnknownRoleException(realm, role);
            }
        }

        return directory.replaceRoles(realm, userId, requested);
    }
}

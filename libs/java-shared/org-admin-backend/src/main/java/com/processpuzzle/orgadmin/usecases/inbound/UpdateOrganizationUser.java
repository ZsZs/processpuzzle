package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.UserNotFoundException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Service;

/**
 * Updates a user's profile and enabled flag.
 *
 * <p>{@code enabled: false} is the preferred way to remove someone: it keeps the account, its group
 * memberships and its roles while making a token unobtainable, so the platform's {@code createdBy}
 * and {@code assignee} references keep resolving. Deletion is the irreversible alternative.
 */
@Service
public class UpdateOrganizationUser {

    private final TenantRealmResolver realms;
    private final UserDirectoryPort directory;

    public UpdateOrganizationUser(TenantRealmResolver realms, UserDirectoryPort directory) {
        this.realms = realms;
        this.directory = directory;
    }

    public DirectoryUser execute(String orgKey, String userId, UserDirectoryPort.UserProfile profile) {
        String realm = realms.resolve(orgKey);
        // Checked here rather than relying on the adapter's 404, so an update to a user that never
        // existed is reported the same way a read of it is.
        if (directory.findUser(realm, userId).isEmpty()) {
            throw new UserNotFoundException(realm, userId);
        }
        return directory.updateUser(realm, userId, profile);
    }
}

package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.UserNotFoundException;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Service;

/**
 * Deletes a user from the tenant's realm.
 *
 * <p>Irreversible, and it removes the identity that the platform's {@code createdBy} and
 * {@code assignee} fields point at — those keep the now-dangling id, because rewriting history to
 * erase a departed person would be worse than a broken link. {@code UpdateOrganizationUser} with
 * {@code enabled: false} is the reversible alternative and is what a client should offer first.
 */
@Service
public class DeleteOrganizationUser {

    private final TenantRealmResolver realms;
    private final UserDirectoryPort directory;

    public DeleteOrganizationUser(TenantRealmResolver realms, UserDirectoryPort directory) {
        this.realms = realms;
        this.directory = directory;
    }

    public void execute(String orgKey, String userId) {
        String realm = realms.resolve(orgKey);
        if (directory.findUser(realm, userId).isEmpty()) {
            throw new UserNotFoundException(realm, userId);
        }
        directory.deleteUser(realm, userId);
    }
}

package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.UserNotFoundException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FindOrganizationUserRoles {

    private final TenantRealmResolver realms;
    private final UserDirectoryPort directory;

    public FindOrganizationUserRoles(TenantRealmResolver realms, UserDirectoryPort directory) {
        this.realms = realms;
        this.directory = directory;
    }

    public List<DirectoryRole> execute(String orgKey, String userId) {
        String realm = realms.resolve(orgKey);
        if (directory.findUser(realm, userId).isEmpty()) {
            throw new UserNotFoundException(realm, userId);
        }
        return directory.findUserRoles(realm, userId);
    }
}

package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.UserNotFoundException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Service;

@Service
public class FindOrganizationUser {

    private final TenantRealmResolver realms;
    private final UserDirectoryPort directory;

    public FindOrganizationUser(TenantRealmResolver realms, UserDirectoryPort directory) {
        this.realms = realms;
        this.directory = directory;
    }

    public DirectoryUser execute(String orgKey, String userId) {
        String realm = realms.resolve(orgKey);
        return directory.findUser(realm, userId)
                .orElseThrow(() -> new UserNotFoundException(realm, userId));
    }
}

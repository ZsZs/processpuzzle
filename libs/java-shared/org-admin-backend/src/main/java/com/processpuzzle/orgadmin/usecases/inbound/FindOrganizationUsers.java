package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Service;

/**
 * Pages through a tenant's users.
 *
 * <p>No {@code @Transactional}: there is nothing transactional to do. The only database read is
 * {@link TenantRealmResolver}'s, which opens its own; the users come from Keycloak.
 */
@Service
public class FindOrganizationUsers {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final TenantRealmResolver realms;
    private final UserDirectoryPort directory;

    public FindOrganizationUsers(TenantRealmResolver realms, UserDirectoryPort directory) {
        this.realms = realms;
        this.directory = directory;
    }

    public DirectoryPage execute(String orgKey, String search, Integer page, Integer size) {
        String realm = realms.resolve(orgKey);
        return directory.findUsers(realm, search,
                page != null ? page : DEFAULT_PAGE,
                size != null ? size : DEFAULT_SIZE);
    }
}

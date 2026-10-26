package com.processpuzzle.orgadmin.usecases.outbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;

import java.util.Collection;

/**
 * Stand-in when there is no identity provider to keep the catalog in.
 *
 * <p>An author's reference is accepted silently, because the roles are a projection of what authors
 * wrote, which is stored regardless. An administrator's change is <em>refused</em> with the same 503
 * the user directory gives: reporting a created role that exists nowhere would be the one lie here
 * that somebody acts on.
 *
 * <p>Deliberately not a {@code @Component}: {@code UserDirectoryConfiguration} registers it.
 */
public class NoOpRoleCatalogPort implements RoleCatalogPort {

    private static final String NO_DIRECTORY =
            "No identity provider is configured, so the role catalog cannot be changed.";

    @Override
    public void ensureRoles(String realm, Collection<String> roleNames, String description) {
        // Nothing to create them in.
    }

    @Override
    public void createRole(String directory, String name, String description) {
        throw new DirectoryUnavailableException(NO_DIRECTORY);
    }

    @Override
    public void updateDescription(String directory, String name, String description) {
        throw new DirectoryUnavailableException(NO_DIRECTORY);
    }

    @Override
    public void deleteRole(String directory, String name) {
        throw new DirectoryUnavailableException(NO_DIRECTORY);
    }

    @Override
    public boolean hasHolders(String directory, String name) {
        throw new DirectoryUnavailableException(NO_DIRECTORY);
    }
}

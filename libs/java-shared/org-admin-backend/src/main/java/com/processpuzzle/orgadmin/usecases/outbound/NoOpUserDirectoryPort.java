package com.processpuzzle.orgadmin.usecases.outbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;

import java.util.List;
import java.util.Optional;

/**
 * Stand-in used when no identity provider is configured, mirroring {@code NoOpIdentityRealmPort} in
 * platform-admin.
 *
 * <p><b>It answers reads as empty and refuses writes.</b> That asymmetry is deliberate and is the one
 * design decision in this class. A no-op that silently accepted an invitation would report success
 * for a user that does not exist and never will — the administrator would discover it when the
 * invitee could not log in, with nothing anywhere to explain why. Refusing with a 503 says the truth:
 * there is no directory to write to. Reads answering empty is harmless by comparison — an empty user
 * list on a deployment with no identity provider is accurate.
 *
 * <p>Deliberately not a {@code @Component}: {@code UserDirectoryConfiguration} registers either this
 * or the Keycloak adapter, so a real adapter never competes with it for injection.
 */
public class NoOpUserDirectoryPort implements UserDirectoryPort {

    private static final String NO_DIRECTORY =
            "No identity provider is configured (keycloak.admin.client-secret is unset), "
                    + "so this organization has no user directory to act on.";

    @Override
    public DirectoryPage findUsers(String realm, String search, int page, int size) {
        return new DirectoryPage(List.of(), 0L, page, size);
    }

    @Override
    public Optional<DirectoryUser> findUser(String realm, String userId) {
        return Optional.empty();
    }

    @Override
    public DirectoryUser inviteUser(String realm, NewUser user, List<String> roles) {
        throw new DirectoryUnavailableException(NO_DIRECTORY);
    }

    @Override
    public DirectoryUser updateUser(String realm, String userId, UserProfile profile) {
        throw new DirectoryUnavailableException(NO_DIRECTORY);
    }

    @Override
    public void deleteUser(String realm, String userId) {
        throw new DirectoryUnavailableException(NO_DIRECTORY);
    }

    @Override
    public List<DirectoryRole> findRoles(String realm) {
        return List.of();
    }

    @Override
    public List<DirectoryRole> findUserRoles(String realm, String userId) {
        return List.of();
    }

    @Override
    public List<DirectoryRole> replaceRoles(String realm, String userId, List<String> roles) {
        throw new DirectoryUnavailableException(NO_DIRECTORY);
    }
}

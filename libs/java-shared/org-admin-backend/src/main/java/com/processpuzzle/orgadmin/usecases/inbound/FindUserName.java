package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.api.UserNameQuery;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.TenantRealmDirectory;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@link UserNameQuery} over the tenant's directory: the user's full name, else their email, else their username.
 *
 * <p><b>Not behind {@link TenantRealmResolver}.</b> That resolver demands design rights, because it guards
 * administering users; reading one name, for a member who has just been assigned a task, is not administration.
 * The realm is found the same way, without the guard. The caller has already authorized its own operation.
 */
@Service
public class FindUserName implements UserNameQuery {

    private static final Logger LOG = LoggerFactory.getLogger(FindUserName.class);

    private final TenantRealmDirectory tenants;
    private final UserDirectoryPort directory;

    public FindUserName(ObjectProvider<TenantRealmDirectory> tenants, UserDirectoryPort directory) {
        this.tenants = tenants.getIfUnique(() -> TenantRealmDirectory.BY_CONVENTION);
        this.directory = directory;
    }

    @Override
    public Optional<String> displayNameOf(String orgKey, String userId) {
        if (orgKey == null || userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        try {
            return tenants.find(orgKey)
                    .flatMap(tenant -> directory.findUser(tenant.realm(), userId))
                    .flatMap(FindUserName::nameOf);
        } catch (RuntimeException ex) {
            // Best effort by contract: the directory being down must not fail the caller's own operation.
            LOG.debug("No name for user {} of {}: the directory did not answer.", userId, orgKey, ex);
            return Optional.empty();
        }
    }

    private static Optional<String> nameOf(DirectoryUser user) {
        String fullName = Stream.of(user.firstName(), user.lastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        return Stream.of(fullName, user.email(), user.username())
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}

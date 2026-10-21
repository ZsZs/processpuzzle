package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.inbound.SelfServiceRoleSupport.Caller;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownRoleException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Makes the caller hold exactly the requested selectable roles.
 *
 * <p>Only the selectable subset is replaced: privileged roles the caller already holds are carried
 * over untouched, so saving the form can neither grant nor revoke them. A requested name outside the
 * selectable set is refused as an unknown role before anything is written.
 */
@Service
public class ReplaceMyRoles {

    private final SelfServiceRoleSupport support;
    private final UserDirectoryPort directory;

    ReplaceMyRoles(SelfServiceRoleSupport support, UserDirectoryPort directory) {
        this.support = support;
        this.directory = directory;
    }

    public MyRoles execute(String orgKey, List<String> roles) {
        Caller caller = support.caller(orgKey);
        Set<String> selectable = support.selectable(caller.realm()).stream()
                .map(DirectoryRole::name).collect(Collectors.toSet());
        List<String> requested = roles == null ? List.of() : roles;
        for (String role : requested) {
            if (!selectable.contains(role)) {
                throw new UnknownRoleException(caller.realm(), role);
            }
        }

        Set<String> target = new LinkedHashSet<>();
        directory.findUserRoles(caller.realm(), caller.userId()).stream()
                .map(DirectoryRole::name)
                .filter(name -> !selectable.contains(name))
                .forEach(target::add);
        target.addAll(requested);
        directory.replaceRoles(caller.realm(), caller.userId(), List.copyOf(target));
        return support.read(caller);
    }
}

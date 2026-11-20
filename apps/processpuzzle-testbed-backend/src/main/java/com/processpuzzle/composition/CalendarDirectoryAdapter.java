package com.processpuzzle.composition;

import com.processpuzzle.calendar.entry.usecases.outbound.CalendarDirectoryPort;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Lets base-calendar find the people to invite in the organization's identity realm, through org-admin's user
 * directory. Only a subject and a name leave this adapter: no e-mail address, no roles, and no disabled account, which
 * cannot be invited to anything.
 *
 * <p>The realm of an organization is its key: that is how the platform names the realms, so this adapter does not
 * need org-admin's realm resolver, which is not part of its public port.
 */
@Component
public class CalendarDirectoryAdapter implements CalendarDirectoryPort {

    private final UserDirectoryPort directory;

    public CalendarDirectoryAdapter(UserDirectoryPort directory) {
        this.directory = directory;
    }

    @Override
    public List<DirectoryMatch> search(String orgKey, String text) {
        return directory.findUsers(orgKey, text, 0, MAX_RESULTS).content().stream()
                .filter(DirectoryUser::enabled)
                .map(CalendarDirectoryAdapter::match)
                .limit(MAX_RESULTS)
                .toList();
    }

    @Override
    public Optional<DirectoryMatch> find(String orgKey, String subject) {
        return directory.findUser(orgKey, subject).filter(DirectoryUser::enabled).map(CalendarDirectoryAdapter::match);
    }

    private static DirectoryMatch match(DirectoryUser user) {
        String name = (nonBlank(user.firstName()) + " " + nonBlank(user.lastName())).strip();
        return new DirectoryMatch(user.id(), name.isEmpty() ? nonBlank(user.username()) : name);
    }

    private static String nonBlank(String value) {
        return value == null ? "" : value.strip();
    }
}

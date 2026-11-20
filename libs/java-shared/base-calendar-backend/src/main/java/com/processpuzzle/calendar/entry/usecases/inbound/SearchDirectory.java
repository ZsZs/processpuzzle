package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.usecases.exception.CalendarAccessDeniedException;
import com.processpuzzle.calendar.entry.usecases.outbound.CalendarDirectoryPort;
import com.processpuzzle.calendar.entry.usecases.outbound.CalendarDirectoryPort.DirectoryMatch;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds people to invite. Members only: a directory of an organization is not for the public. A query shorter than
 * two characters finds nothing, so the search cannot be used to list everybody a character at a time.
 */
@Service
@Transactional(readOnly = true)
public class SearchDirectory {

    /** The shortest query that searches. */
    public static final int MIN_QUERY = 2;

    private final CalendarDirectoryPort directory;
    private final CallerMembership membership;

    public SearchDirectory(ObjectProvider<CalendarDirectoryPort> directory, CallerMembership membership) {
        this.directory = directory.getIfUnique(() -> EmptyDirectory.INSTANCE);
        this.membership = membership;
    }

    public List<DirectoryMatch> search(String orgKey, String query) {
        if (!membership.isMember(orgKey)) {
            throw new CalendarAccessDeniedException("Sign in to find people to invite");
        }
        String text = query == null ? "" : query.strip();
        if (text.length() < MIN_QUERY) {
            return List.of();
        }
        return directory.search(orgKey, text).stream().limit(CalendarDirectoryPort.MAX_RESULTS).toList();
    }
}

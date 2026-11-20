package com.processpuzzle.calendar.entry.usecases.outbound;

import java.util.List;
import java.util.Optional;

/**
 * The people of an organization, as far as an invitation needs them: a subject and a name. The host application
 * adapts its identity provider's directory; the module never sees an e-mail address or a role through this port.
 *
 * <p>Without an adapter nobody can be found, so nobody can be invited by name. That is the honest default: the
 * alternative, accepting any subject, would let an invitation reach a stranger.
 */
public interface CalendarDirectoryPort {

    /** The members of the organization whose name matches {@code text}; at most {@link #MAX_RESULTS}. */
    List<DirectoryMatch> search(String orgKey, String text);

    /** The member with this subject, or empty when the subject is not a member of the organization. */
    Optional<DirectoryMatch> find(String orgKey, String subject);

    /** The most matches one search returns. */
    int MAX_RESULTS = 20;

    /** A member as an invitation sees them. */
    record DirectoryMatch(String sub, String displayName) {
    }
}

package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.usecases.outbound.CalendarDirectoryPort;
import java.util.List;
import java.util.Optional;

/**
 * What a host without a directory adapter gets: nobody is found, so nobody can be invited by name. The honest default:
 * the alternative, accepting any subject, would let an invitation reach a stranger.
 */
public final class EmptyDirectory implements CalendarDirectoryPort {

    public static final CalendarDirectoryPort INSTANCE = new EmptyDirectory();

    private EmptyDirectory() {
    }

    @Override
    public List<DirectoryMatch> search(String orgKey, String text) {
        return List.of();
    }

    @Override
    public Optional<DirectoryMatch> find(String orgKey, String subject) {
        return Optional.empty();
    }
}

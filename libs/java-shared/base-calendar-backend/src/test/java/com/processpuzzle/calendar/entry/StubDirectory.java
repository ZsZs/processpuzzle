package com.processpuzzle.calendar.entry;

import com.processpuzzle.calendar.entry.usecases.outbound.CalendarDirectoryPort;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** A directory of named people for the tests: subject to display name, as the host's identity provider would hold them. */
public final class StubDirectory implements CalendarDirectoryPort {

    private final Map<String, String> people;

    public StubDirectory(Map<String, String> people) {
        this.people = Map.copyOf(people);
    }

    @Override
    public List<DirectoryMatch> search(String orgKey, String text) {
        String needle = text.toLowerCase(Locale.ROOT);
        return people.entrySet().stream()
                .filter(entry -> entry.getValue().toLowerCase(Locale.ROOT).contains(needle))
                .map(entry -> new DirectoryMatch(entry.getKey(), entry.getValue()))
                .limit(MAX_RESULTS)
                .toList();
    }

    @Override
    public Optional<DirectoryMatch> find(String orgKey, String subject) {
        return Optional.ofNullable(people.get(subject)).map(name -> new DirectoryMatch(subject, name));
    }
}

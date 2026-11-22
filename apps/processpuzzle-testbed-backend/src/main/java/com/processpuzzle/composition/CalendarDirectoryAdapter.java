package com.processpuzzle.composition;

import com.processpuzzle.calendar.entry.usecases.outbound.CalendarDirectoryPort;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** Candidates fetched per word, so that a word common to many people still leaves the others in the intersection. */
    private static final int WORD_LIMIT = 100;
    private static final int MAX_WORDS = 4;

    private final UserDirectoryPort directory;

    public CalendarDirectoryAdapter(UserDirectoryPort directory) {
        this.directory = directory;
    }

    /**
     * Each word of the text must be found somewhere in the person's name or username, so "Bob Builder" finds Bob Builder.
     * Keycloak searches for one term at a time, so each word is searched on its own and the answers are intersected.
     */
    @Override
    public List<DirectoryMatch> search(String orgKey, String text) {
        List<String> words = words(text);
        if (words.isEmpty()) {
            return List.of();
        }
        Map<String, DirectoryUser> found = null;
        for (String word : words) {
            Map<String, DirectoryUser> byWord = new LinkedHashMap<>();
            for (DirectoryUser user : directory.findUsers(orgKey, infix(word), 0, WORD_LIMIT).content()) {
                if (user.enabled()) {
                    byWord.put(user.id(), user);
                }
            }
            found = found == null ? byWord : intersect(found, byWord);
            if (found.isEmpty()) {
                return List.of();
            }
        }
        return found.values().stream().map(CalendarDirectoryAdapter::match).limit(MAX_RESULTS).toList();
    }

    @Override
    public Optional<DirectoryMatch> find(String orgKey, String subject) {
        return directory.findUser(orgKey, subject).filter(DirectoryUser::enabled).map(CalendarDirectoryAdapter::match);
    }

    private static DirectoryMatch match(DirectoryUser user) {
        String name = (nonBlank(user.firstName()) + " " + nonBlank(user.lastName())).strip();
        return new DirectoryMatch(user.id(), name.isEmpty() ? nonBlank(user.username()) : name);
    }

    /**
     * Keycloak matches a search term at the start of a field unless it is wrapped in asterisks, so a person is found by
     * any part of their name or username. A typed asterisk is removed, so the wrapping cannot be widened by the user.
     */
    static String infix(String text) {
        return "*" + text.replace("*", "").strip() + "*";
    }

    /** The distinct words of a search, at most {@link #MAX_WORDS}; a longer search is narrowed by its first words. */
    static List<String> words(String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.replace("*", " ").strip().split("\\s+"))
                .filter(word -> !word.isEmpty())
                .distinct()
                .limit(MAX_WORDS)
                .toList();
    }

    private static Map<String, DirectoryUser> intersect(Map<String, DirectoryUser> left, Map<String, DirectoryUser> right) {
        Map<String, DirectoryUser> both = new LinkedHashMap<>();
        left.forEach((id, user) -> {
            if (right.containsKey(id)) {
                both.put(id, user);
            }
        });
        return both;
    }

    private static String nonBlank(String value) {
        return value == null ? "" : value.strip();
    }
}

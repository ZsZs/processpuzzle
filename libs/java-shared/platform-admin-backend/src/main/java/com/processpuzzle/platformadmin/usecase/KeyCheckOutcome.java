package com.processpuzzle.platformadmin.usecase;

import java.util.List;

/**
 * Result of checking whether an organization key can be claimed.
 *
 * @param key the key that was checked
 * @param available whether it can be claimed
 * @param errorId why not, as a Transloco key; {@code null} when available
 * @param suggestions available alternatives derived from the requested key
 */
public record KeyCheckOutcome(String key, boolean available, String errorId, List<String> suggestions) {

    public KeyCheckOutcome {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public static KeyCheckOutcome available(String key) {
        return new KeyCheckOutcome(key, true, null, List.of());
    }

    public static KeyCheckOutcome unavailable(String key, String errorId, List<String> suggestions) {
        return new KeyCheckOutcome(key, false, errorId, suggestions);
    }
}

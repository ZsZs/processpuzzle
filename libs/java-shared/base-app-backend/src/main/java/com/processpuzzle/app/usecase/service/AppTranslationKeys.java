package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.usecase.exception.ModuleTranslationRejectedException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What an app's translations may hold. A module's keys are fixed by its routes, so an upload can tell which are
 * unknown; an app's are whatever its widgets name, so the rules here are about shape and size only — enough that a
 * bundle cannot be a vehicle for something else, and cannot grow without bound.
 *
 * <p>A key is letters, digits, {@code _}, {@code -} and {@code .} (the dot being how a nested file is flattened),
 * which is everything a transloco key is in this platform's bundles.
 */
public final class AppTranslationKeys {

    static final int MAX_KEYS = 2000;
    static final int MAX_KEY_LENGTH = 200;
    static final int MAX_TEXT_LENGTH = 4000;

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]+");

    private AppTranslationKeys() {
    }

    /** Refuses the bundle of {@code locale} as a whole when a key or text is outside the limits. */
    public static void require(String locale, Map<String, String> messages) {
        if (messages.size() > MAX_KEYS) {
            throw new ModuleTranslationRejectedException("'" + locale + "' holds more than " + MAX_KEYS + " texts.");
        }
        messages.forEach((key, text) -> {
            if (key.length() > MAX_KEY_LENGTH || !KEY.matcher(key).matches() || key.startsWith(".") || key.endsWith(".")
                    || key.contains("..")) {
                throw new ModuleTranslationRejectedException("'" + locale + "': '" + key
                        + "' is not a valid key (letters, digits, _, - and . in between, at most " + MAX_KEY_LENGTH + " characters).");
            }
            if (text.length() > MAX_TEXT_LENGTH) {
                throw new ModuleTranslationRejectedException("'" + locale + "': the text of '" + key + "' is longer than "
                        + MAX_TEXT_LENGTH + " characters.");
            }
        });
    }
}

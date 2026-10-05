package com.processpuzzle.core.definition;

/**
 * One definition a participant wrote.
 *
 * @param key    the definition's key within its kind and organization — an entity code, a state
 *               machine's entity name, a rule id
 * @param action whether the definition was new or replaced an existing one
 */
public record ImportedItem(String key, Action action) {

    public enum Action {
        CREATE, UPDATE
    }
}

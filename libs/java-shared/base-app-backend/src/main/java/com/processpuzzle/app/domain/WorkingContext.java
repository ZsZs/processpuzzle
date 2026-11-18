package com.processpuzzle.app.domain;

import java.util.List;
import java.util.Map;

/**
 * A working context, as persisted inside {@link AppGraph}: an entity type the user selects once and then
 * works inside. Nothing here knows what the entity is; routes opt in with {@link AppRoute#requiresContext()}.
 *
 * @param name the context's name and URL segment ({@code ctx/<name>/<id>/...}); unique within the app
 * @param entityName entity type acting as the context root
 * @param parent name of the parent context, or {@code null}
 * @param basePath absolute path the context's routes are mounted under, or {@code null} for the app root
 * @param picker how the entity is picked, or {@code null} for the defaults
 * @param display what the status bar shows of the active entity, or {@code null} for the title attribute
 * @param confirmSwitchWhen conditions on the active entity that make leaving it ask first
 * @param persist {@code url} or {@code url-local-storage}; {@code null} means the latter
 */
public record WorkingContext(
        String name,
        String entityName,
        String parent,
        String basePath,
        Picker picker,
        Display display,
        Map<String, Object> confirmSwitchWhen,
        String persist) {

    public WorkingContext {
        confirmSwitchWhen = confirmSwitchWhen == null ? null : Map.copyOf(confirmSwitchWhen);
    }

    /** @param searchFields attributes the picker's search matches @param recent recently active entities offered */
    public record Picker(List<String> searchFields, Integer recent) {
        public Picker {
            searchFields = searchFields == null ? List.of() : List.copyOf(searchFields);
        }
    }

    /** @param label template for the crumb text @param badge attribute shown as a badge @param extra template for a second text */
    public record Display(String label, String badge, String extra) {}
}

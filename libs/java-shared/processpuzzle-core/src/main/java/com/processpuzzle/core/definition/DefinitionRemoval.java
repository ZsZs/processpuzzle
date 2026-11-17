package com.processpuzzle.core.definition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What a participant did when asked to remove some definitions: the keys it removed, and for each it kept the reason —
 * a definition something still depends on stays, and the upgrade reports it instead of failing as a whole.
 */
public record DefinitionRemoval(List<String> removed, Map<String, String> refused) {

    public DefinitionRemoval {
        removed = removed == null ? List.of() : List.copyOf(removed);
        refused = refused == null ? Map.of() : Map.copyOf(refused);
    }

    /**
     * Deletes {@code keys} one by one with {@code delete}, which throws when a definition cannot go. A definition that
     * something else still depends on may become deletable once its dependent is gone, so the refused ones are tried
     * again for as long as a pass removes something; what remains is refused, with the last reason.
     */
    public static DefinitionRemoval removing(Collection<String> keys, Consumer<String> delete) {
        List<String> pending = new ArrayList<>(keys);
        List<String> removed = new ArrayList<>();
        Map<String, String> refused = new LinkedHashMap<>();
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            for (String key : List.copyOf(pending)) {
                try {
                    delete.accept(key);
                    removed.add(key);
                    pending.remove(key);
                    refused.remove(key);
                    progress = true;
                } catch (RuntimeException e) {
                    refused.put(key, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
            }
        }
        return new DefinitionRemoval(removed, refused);
    }
}

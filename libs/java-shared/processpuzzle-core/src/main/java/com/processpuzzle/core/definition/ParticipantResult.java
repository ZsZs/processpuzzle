package com.processpuzzle.core.definition;

import java.util.List;

/**
 * What a participant did with one file: the definitions it wrote, or the reasons it refused the file.
 * A non-empty {@code errors} means nothing from the file may stay written; the importer rolls back.
 */
public record ParticipantResult(List<ImportedItem> items, List<String> errors) {

    public ParticipantResult {
        items = items == null ? List.of() : List.copyOf(items);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ParticipantResult rejected(List<String> errors) {
        return new ParticipantResult(List.of(), errors);
    }

    public boolean isRejected() {
        return !errors.isEmpty();
    }
}

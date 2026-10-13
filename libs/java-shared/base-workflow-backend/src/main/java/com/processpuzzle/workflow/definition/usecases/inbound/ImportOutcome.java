package com.processpuzzle.workflow.definition.usecases.inbound;

import java.util.List;
import java.util.Map;

/**
 * Result of a YAML import. All-or-nothing: when {@code errors} is non-empty nothing was
 * persisted and both counters are zero. Mirrors {@code ImportOutcome} in base-app-backend /
 * base-rule-backend / base-document-backend.
 */
public record ImportOutcome(int created, int updated, List<String> errors, Map<String, List<String>> written) {

    /**
     * @param written the ids of the drafts the import wrote, created or updated, by definition kind
     *                ({@code DefinitionKinds}) — what a loader that seeds a vetted set releases, and only that
     */
    public ImportOutcome {
        errors = errors == null ? List.of() : List.copyOf(errors);
        written = written == null ? Map.of() : Map.copyOf(written);
    }

    public ImportOutcome(int created, int updated, List<String> errors) {
        this(created, updated, errors, Map.of());
    }

    public static ImportOutcome rejected(List<String> errors) {
        return new ImportOutcome(0, 0, errors);
    }

    /** The ids written of one kind; empty when the import wrote none. */
    public List<String> written(String kind) {
        return written.getOrDefault(kind, List.of());
    }
}

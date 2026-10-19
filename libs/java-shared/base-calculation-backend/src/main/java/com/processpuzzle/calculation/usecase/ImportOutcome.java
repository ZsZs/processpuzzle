package com.processpuzzle.calculation.usecase;

import java.util.List;

/**
 * Result of a YAML import. All-or-nothing: when {@code errors} is non-empty nothing was persisted and both counters
 * are zero. Mirrors {@code ImportOutcome} of the other feature libraries.
 *
 * @param written the ids of the drafts the import wrote, created or updated — what a seed loader releases
 */
public record ImportOutcome(int created, int updated, List<String> errors, List<String> written) {

    public ImportOutcome {
        errors = errors == null ? List.of() : List.copyOf(errors);
        written = written == null ? List.of() : List.copyOf(written);
    }

    public static ImportOutcome rejected(List<String> errors) {
        return new ImportOutcome(0, 0, errors, List.of());
    }
}

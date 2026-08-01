package com.processpuzzle.app.usecase;

import java.util.List;

/**
 * Result of a YAML import. All-or-nothing: when {@code errors} is non-empty nothing was persisted
 * and both counters are zero.
 *
 * @param created number of new app definitions created
 * @param updated number of existing app definitions replaced
 * @param errors validation errors that caused the import to be rejected
 */
public record ImportOutcome(int created, int updated, List<String> errors) {

    public ImportOutcome {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ImportOutcome rejected(List<String> errors) {
        return new ImportOutcome(0, 0, errors);
    }
}

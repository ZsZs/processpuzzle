package com.processpuzzle.workflow.definition.usecases.inbound;

import java.util.List;

/**
 * Result of a YAML import. All-or-nothing: when {@code errors} is non-empty nothing was
 * persisted and both counters are zero. Mirrors {@code ImportOutcome} in base-app-backend /
 * base-rule-backend / base-document-backend.
 */
public record ImportOutcome(int created, int updated, List<String> errors) {

    public ImportOutcome {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ImportOutcome rejected(List<String> errors) {
        return new ImportOutcome(0, 0, errors);
    }
}

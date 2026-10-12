package com.processpuzzle.rule.usecase;

import java.util.List;

/**
 * @param written the ids of the rules the import created or updated, in file order
 */
public record ImportOutcome(int created, int updated, List<String> errors, List<String> written) {

    public ImportOutcome {
        written = written == null ? List.of() : List.copyOf(written);
    }

    /** An outcome that wrote nothing, or whose ids nobody asks for. */
    public ImportOutcome(int created, int updated, List<String> errors) {
        this(created, updated, errors, List.of());
    }
}

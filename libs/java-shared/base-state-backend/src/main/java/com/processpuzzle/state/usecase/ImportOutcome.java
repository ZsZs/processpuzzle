package com.processpuzzle.state.usecase;

import java.util.List;

/**
 * @param written the entity names whose machines the import created or updated, in file order
 */
public record ImportOutcome(int created, int updated, List<String> errors, List<String> written) {

    public ImportOutcome {
        written = written == null ? List.of() : List.copyOf(written);
    }

    /** An outcome that wrote nothing, or whose names nobody asks for. */
    public ImportOutcome(int created, int updated, List<String> errors) {
        this(created, updated, errors, List.of());
    }
}

package com.processpuzzle.document.usecase;

import java.util.List;

public record ImportOutcome(int created, int updated, List<String> errors) {

    public ImportOutcome {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}

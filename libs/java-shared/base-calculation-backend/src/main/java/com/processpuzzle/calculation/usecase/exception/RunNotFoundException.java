package com.processpuzzle.calculation.usecase.exception;

import java.util.UUID;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(String orgKey, UUID runId) {
        super("No calculation run '%s' in organization '%s'".formatted(runId, orgKey));
    }
}

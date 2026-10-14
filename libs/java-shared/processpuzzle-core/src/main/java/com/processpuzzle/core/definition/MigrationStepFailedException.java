package com.processpuzzle.core.definition;

/** A {@link MigrationStep} that could not change every object it was meant to; the message says which and why. */
public class MigrationStepFailedException extends RuntimeException {

    public MigrationStepFailedException(String message) {
        super(message);
    }
}

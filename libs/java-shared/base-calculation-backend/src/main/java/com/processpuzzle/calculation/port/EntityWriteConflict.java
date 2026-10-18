package com.processpuzzle.calculation.port;

/** A write of {@link EntityWritePort} that must not land: the object moved on, or is gone. */
public class EntityWriteConflict extends RuntimeException {

    public EntityWriteConflict(String message) {
        super(message);
    }
}

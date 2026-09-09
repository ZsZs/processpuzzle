package com.processpuzzle.state.usecase.exception;

import java.util.UUID;

/**
 * Thrown by {@code EntityObjectGateway.updateStateAttribute} when {@code expectedVersion} no
 * longer matches the object's current version — a genuine optimistic-lock conflict, distinct from
 * a transition simply not being available (see {@code TransitionOutcome.rejectionReason} for
 * that case). Mapped to {@code 409} by {@code StateApiExceptionHandler}.
 */
public class StaleEntityObjectVersionException extends RuntimeException {

    public StaleEntityObjectVersionException(UUID objectId, long expectedVersion) {
        super("Entity object '" + objectId + "' has changed since version " + expectedVersion + " was read");
    }
}

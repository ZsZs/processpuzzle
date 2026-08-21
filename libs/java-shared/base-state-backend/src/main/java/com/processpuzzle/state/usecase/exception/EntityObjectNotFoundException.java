package com.processpuzzle.state.usecase.exception;

import java.util.UUID;

/**
 * Thrown by {@code EntityObjectGateway} implementations when no such object exists. Owned by
 * base-state rather than left to each adapter to invent its own: a port's failure modes are part
 * of its contract, and {@code StateApiExceptionHandler} needs one stable type to map to 404
 * regardless of which adapter is wired.
 */
public class EntityObjectNotFoundException extends RuntimeException {

    public EntityObjectNotFoundException(String orgKey, String entityName, UUID objectId) {
        super("No entity object '" + objectId + "' of type '" + entityName + "' in organization '" + orgKey + "'");
    }
}

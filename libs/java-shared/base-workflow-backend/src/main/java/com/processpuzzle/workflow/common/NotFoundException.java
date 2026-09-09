package com.processpuzzle.workflow.common;

// Note: processpuzzle-core almost certainly already has an exception hierarchy for this
// (mirroring RsqlSpecificationBuilder living there) — swap this out once its shape is confirmed.
// Kept minimal and local for now, same as base-entity-backend and base-rule-backend do.
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}

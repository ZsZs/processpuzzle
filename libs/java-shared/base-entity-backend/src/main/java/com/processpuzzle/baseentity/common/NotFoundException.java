package com.processpuzzle.baseentity.common;

// TODO: processpuzzle-core almost certainly already has an exception hierarchy for this
// (mirroring RsqlSpecificationBuilder living there for RSQL) — swap this out once its shape
// is confirmed. Kept minimal and local for now so the module compiles and the exception
// handling story is at least centralized in ONE place pending that.
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}

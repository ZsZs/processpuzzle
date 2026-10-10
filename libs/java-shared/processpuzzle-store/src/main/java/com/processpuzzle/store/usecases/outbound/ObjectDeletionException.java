package com.processpuzzle.store.usecases.outbound;

/** The object store refused to delete one or more objects, or could not be asked to. */
public class ObjectDeletionException extends RuntimeException {

    public ObjectDeletionException(String message, Throwable cause) {
        super(message, cause);
    }
}

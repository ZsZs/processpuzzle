package com.processpuzzle.orgadmin.usecases.inbound.exception;

/**
 * The user directory could not be reached, or refused the operation. Surfaced as 503.
 *
 * <p>Not a 500, because the state is knowable: the directory is the system of record and this module
 * keeps no copy, so a failure means the operation did not happen and the same request can be sent
 * again. A 500 would leave the caller unable to tell whether to retry.
 */
public class DirectoryUnavailableException extends RuntimeException {

    public DirectoryUnavailableException(String message) {
        super(message);
    }

    public DirectoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

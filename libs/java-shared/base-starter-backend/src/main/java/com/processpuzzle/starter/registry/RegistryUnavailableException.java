package com.processpuzzle.starter.registry;

/** The registry could not be read — unreachable, an HTTP error, or content that does not parse. */
public class RegistryUnavailableException extends RuntimeException {

    public RegistryUnavailableException(String message) {
        super(message);
    }

    public RegistryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

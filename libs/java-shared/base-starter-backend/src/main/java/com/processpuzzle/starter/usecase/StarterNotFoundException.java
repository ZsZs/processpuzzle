package com.processpuzzle.starter.usecase;

/** The catalog has no such starter, or no installable version of that number. */
public class StarterNotFoundException extends RuntimeException {

    public StarterNotFoundException(String message) {
        super(message);
    }
}

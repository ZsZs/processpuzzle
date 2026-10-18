package com.processpuzzle.calculation.usecase.exception;

import java.util.List;

/** A calculation source that does not parse or does not validate; answered 400. */
public class InvalidCalculationException extends RuntimeException {

    private final List<String> errors;

    public InvalidCalculationException(String id, List<String> errors) {
        super("Calculation '%s' is invalid: %s".formatted(id, String.join("; ", errors)));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}

package com.processpuzzle.calculation.usecase.exception;

public class StaleCalculationException extends RuntimeException {

    public StaleCalculationException(String id) {
        super("Calculation '%s' was modified concurrently — reload and retry".formatted(id));
    }
}

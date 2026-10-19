package com.processpuzzle.calculation.usecase.exception;

public class CalculationNotFoundException extends RuntimeException {

    public CalculationNotFoundException(String orgKey, String id) {
        super("No calculation '%s' in organization '%s'".formatted(id, orgKey));
    }
}

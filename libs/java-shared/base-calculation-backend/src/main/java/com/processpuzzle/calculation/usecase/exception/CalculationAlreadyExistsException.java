package com.processpuzzle.calculation.usecase.exception;

public class CalculationAlreadyExistsException extends RuntimeException {

    public CalculationAlreadyExistsException(String orgKey, String id) {
        super("Calculation '%s' already exists in organization '%s'".formatted(id, orgKey));
    }
}

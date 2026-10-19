package com.processpuzzle.calculation.engine;

/** A FEEL expression that failed — to parse or to evaluate; what {@link ExpressionEnginePort} throws. */
public class ExpressionException extends RuntimeException {

    public ExpressionException(String expression, String message) {
        super("'%s': %s".formatted(expression, message));
    }
}

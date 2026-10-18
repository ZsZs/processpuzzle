package com.processpuzzle.calculation.usecase.exception;

/** A parameter is missing, of the wrong kind, or names an object that does not exist; answered 400. */
public class InvalidParametersException extends RuntimeException {

    public InvalidParametersException(String message) {
        super(message);
    }
}

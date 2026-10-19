package com.processpuzzle.calculation.usecase.exception;

/** Only a completed run can be frozen; answered 409. */
public class RunNotFreezableException extends RuntimeException {

    public RunNotFreezableException(String message) {
        super(message);
    }
}

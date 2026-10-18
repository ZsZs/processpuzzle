package com.processpuzzle.calculation.usecase.exception;

/** The subject's result of a calculation is frozen, so it is not recalculated; answered 409. */
public class SubjectFrozenException extends RuntimeException {

    public SubjectFrozenException(String calculationId, String subjectType, String subjectId) {
        super("The result of '%s' for %s '%s' is frozen".formatted(calculationId, subjectType, subjectId));
    }
}

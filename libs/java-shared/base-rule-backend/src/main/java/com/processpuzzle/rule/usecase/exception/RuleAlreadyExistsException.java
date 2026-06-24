package com.processpuzzle.rule.usecase.exception;

public class RuleAlreadyExistsException extends RuntimeException {

    public RuleAlreadyExistsException(String id) {
        super("Rule already exists: " + id);
    }
}

package com.processpuzzle.rule.usecase.exception;

public class RuleNotFoundException extends RuntimeException {

    public RuleNotFoundException(String id) {
        super("Rule not found: " + id);
    }
}

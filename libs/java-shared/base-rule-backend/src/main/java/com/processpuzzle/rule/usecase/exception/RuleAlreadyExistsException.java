package com.processpuzzle.rule.usecase.exception;

public class RuleAlreadyExistsException extends RuntimeException {

    public RuleAlreadyExistsException(String orgKey, String id) {
        super("Rule already exists: " + orgKey + "/" + id);
    }
}

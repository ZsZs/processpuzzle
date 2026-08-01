package com.processpuzzle.rule.usecase.exception;

public class RuleNotFoundException extends RuntimeException {

    public RuleNotFoundException(String orgKey, String id) {
        super("Rule not found: " + orgKey + "/" + id);
    }
}

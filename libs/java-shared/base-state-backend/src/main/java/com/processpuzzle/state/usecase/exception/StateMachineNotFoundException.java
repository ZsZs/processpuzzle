package com.processpuzzle.state.usecase.exception;

public class StateMachineNotFoundException extends RuntimeException {

    public StateMachineNotFoundException(String orgKey, String entityName) {
        super("No state machine definition for entityName '" + entityName + "' in organization '" + orgKey + "'");
    }
}

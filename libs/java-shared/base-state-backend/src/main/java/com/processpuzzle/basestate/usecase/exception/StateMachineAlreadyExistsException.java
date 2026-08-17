package com.processpuzzle.basestate.usecase.exception;

public class StateMachineAlreadyExistsException extends RuntimeException {

    public StateMachineAlreadyExistsException(String orgKey, String entityName) {
        super("entityName '" + entityName + "' already has a state machine definition in organization '" + orgKey + "'");
    }
}

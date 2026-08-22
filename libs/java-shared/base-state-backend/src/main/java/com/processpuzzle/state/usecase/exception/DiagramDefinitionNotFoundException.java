package com.processpuzzle.state.usecase.exception;

public class DiagramDefinitionNotFoundException extends RuntimeException {

    public DiagramDefinitionNotFoundException(String orgKey, String entityName) {
        super("No diagram layout for entityName '" + entityName + "' in organization '" + orgKey + "'");
    }
}

package com.processpuzzle.widget.usecase.exception;

/** A widget definition with the given key already exists in the organization. Answered as 409. */
public class WidgetDefinitionAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WidgetDefinitionAlreadyExistsException(String orgKey, String key) {
        super("Widget definition '" + key + "' already exists in organization '" + orgKey + "'.");
    }
}

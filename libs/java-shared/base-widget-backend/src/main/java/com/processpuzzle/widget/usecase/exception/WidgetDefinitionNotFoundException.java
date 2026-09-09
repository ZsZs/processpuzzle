package com.processpuzzle.widget.usecase.exception;

/** No widget definition with the given key exists in the organization. Answered as 404. */
public class WidgetDefinitionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WidgetDefinitionNotFoundException(String orgKey, String key) {
        super("Widget definition '" + key + "' not found in organization '" + orgKey + "'.");
    }
}

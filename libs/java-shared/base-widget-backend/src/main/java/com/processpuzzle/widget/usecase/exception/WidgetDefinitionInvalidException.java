package com.processpuzzle.widget.usecase.exception;

/**
 * The submitted definition failed a structural check — a malformed key, a blank name, or duplicate
 * port names. Answered as 400.
 *
 * <p>Deliberately not raised for props that disagree with {@code propsSchema}: this backend does not
 * validate instance props against the schema. The widget owns its props, and enforcement belongs
 * where they are edited. See the contract's note on propsSchema.
 */
public class WidgetDefinitionInvalidException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WidgetDefinitionInvalidException(String message) {
        super(message);
    }
}

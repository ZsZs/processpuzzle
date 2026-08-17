package com.processpuzzle.baseentity.common;

import java.io.Serializable;
import java.util.List;
import lombok.Getter;

/**
 * Exception thrown when an entity definition or payload fails validation.
 */
@Getter
public class ValidationException extends RuntimeException {

    private final List<Violation> violations;

    public ValidationException(List<Violation> violations) {
        super("Payload failed validation");
        this.violations = violations;
    }

    public record Violation(String attributeCode, String message) implements Serializable {
    }
}

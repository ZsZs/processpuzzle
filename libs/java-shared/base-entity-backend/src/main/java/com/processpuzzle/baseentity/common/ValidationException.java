package com.processpuzzle.baseentity.common;

import java.util.List;
import lombok.Getter;

// TODO: same note as NotFoundException — replace with processpuzzle-core's equivalent.
@Getter
public class ValidationException extends RuntimeException {

    private final List<Violation> violations;

    public ValidationException(List<Violation> violations) {
        super("Payload failed validation");
        this.violations = violations;
    }

    public record Violation(String attributeCode, String message) {
    }
}

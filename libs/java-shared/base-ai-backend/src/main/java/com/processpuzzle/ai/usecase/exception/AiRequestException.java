package com.processpuzzle.ai.usecase.exception;

import lombok.Getter;

/**
 * Every refusal of this feature's use cases. One class with a {@link Kind} rather than one class per
 * refusal: the handler needs only the kind to choose a status and the {@code errorId} to tell the
 * frontend which Transloco key to show, and a dozen near-empty subclasses would add nothing to that.
 */
@Getter
public class AiRequestException extends RuntimeException {

    public enum Kind { NOT_FOUND, INVALID, CONFLICT, TOO_LARGE, UNAUTHORIZED }

    private final Kind kind;
    private final String errorId;

    public AiRequestException(Kind kind, String errorId, String message) {
        super(message);
        this.kind = kind;
        this.errorId = errorId;
    }

    public static AiRequestException notFound(String errorId, String message) {
        return new AiRequestException(Kind.NOT_FOUND, errorId, message);
    }

    public static AiRequestException invalid(String errorId, String message) {
        return new AiRequestException(Kind.INVALID, errorId, message);
    }

    public static AiRequestException conflict(String errorId, String message) {
        return new AiRequestException(Kind.CONFLICT, errorId, message);
    }
}

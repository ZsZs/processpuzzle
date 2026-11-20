package com.processpuzzle.core.quantity;

/** A fundamental-type operation refused its input. The reasons have the same names as the frontend's {@code FundamentalTypeError}. */
public class FundamentalTypeException extends RuntimeException {

    /** Why the operation was refused. */
    public enum Reason {
        INVALID_AMOUNT,
        UNKNOWN_UNIT,
        UNKNOWN_CURRENCY,
        UNKNOWN_KIND,
        KIND_MISMATCH,
        CURRENCY_MISMATCH,
        OFFSET_ARITHMETIC,
        DIVISION_BY_ZERO,
        INVALID_DURATION,
        CALENDAR_DURATION,
        INVALID_CATALOG
    }

    private final Reason reason;

    public FundamentalTypeException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public FundamentalTypeException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}

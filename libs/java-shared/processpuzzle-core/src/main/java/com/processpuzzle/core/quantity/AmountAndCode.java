package com.processpuzzle.core.quantity;

/** Splits {@code "<amount> <code>"}, the literal form of a quantity and of money. */
final class AmountAndCode {

    private AmountAndCode() {
    }

    static String[] split(String text) {
        String[] parts = text == null ? new String[0] : text.trim().split("\\s+");
        if (parts.length != 2) {
            throw new FundamentalTypeException(FundamentalTypeException.Reason.INVALID_AMOUNT, "'%s' is not '<amount> <unit>'".formatted(text));
        }
        return parts;
    }
}

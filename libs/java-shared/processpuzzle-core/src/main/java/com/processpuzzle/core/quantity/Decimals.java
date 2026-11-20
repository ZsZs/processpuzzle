package com.processpuzzle.core.quantity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * How every fundamental type computes: a division keeps {@link #DIVISION_PLACES} decimal places, rounding half up — what
 * the frontend's big.js constructor is configured to do, which keeps both sides' results digit for digit the same.
 */
public final class Decimals {

    public static final int DIVISION_PLACES = 20;

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");

    private Decimals() {
    }

    /** A decimal from its stored text ({@code "12.50"}); anything else is an INVALID_AMOUNT. */
    public static BigDecimal parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!PLAIN_DECIMAL.matcher(trimmed).matches()) {
            throw new FundamentalTypeException(FundamentalTypeException.Reason.INVALID_AMOUNT, "'%s' is not a decimal number".formatted(text));
        }
        return new BigDecimal(trimmed);
    }

    /** The decimal without trailing zeros, and zero as plain {@code 0}; the form every value keeps its amounts in. */
    public static BigDecimal normalize(BigDecimal value) {
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }

    /** The stored text of a decimal: plain notation, no trailing zeros, never {@code -0}. */
    public static String text(BigDecimal value) {
        return normalize(value).toPlainString();
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor.signum() == 0) {
            throw new FundamentalTypeException(FundamentalTypeException.Reason.DIVISION_BY_ZERO, "division by zero");
        }
        return dividend.divide(divisor, DIVISION_PLACES, RoundingMode.HALF_UP);
    }
}

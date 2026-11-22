package com.processpuzzle.core.quantity;

import com.processpuzzle.core.quantity.FundamentalTypeException.Reason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * How many units of {@code to} one unit of {@code from} is worth, from {@code validFrom} on — until a later rate of the same
 * pair starts. A rate is a positive decimal between two different currencies.
 */
public record ExchangeRate(String from, String to, BigDecimal rate, LocalDate validFrom) {

    public ExchangeRate {
        if (from == null || from.isBlank() || to == null || to.isBlank() || from.equals(to)) {
            throw new FundamentalTypeException(Reason.INVALID_RATE, "an exchange rate is between two different currencies");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new FundamentalTypeException(Reason.INVALID_RATE, "the rate of %s to %s must be a positive decimal".formatted(from, to));
        }
        if (validFrom == null) {
            throw new FundamentalTypeException(Reason.INVALID_RATE, "the rate of %s to %s needs the date it is valid from".formatted(from, to));
        }
        rate = Decimals.normalize(rate);
    }

    /** A rate from its wire text; a date that is not an ISO date ({@code 2026-07-01}) is an INVALID_RATE. */
    public static ExchangeRate of(String from, String to, String rate, String validFrom) {
        return new ExchangeRate(from, to, Decimals.parse(rate), isoDate(validFrom));
    }

    private static LocalDate isoDate(String text) {
        try {
            return text == null ? null : LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new FundamentalTypeException(Reason.INVALID_RATE, "'%s' is not an ISO date".formatted(text), e);
        }
    }
}

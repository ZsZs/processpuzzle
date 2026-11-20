package com.processpuzzle.core.quantity;

import com.processpuzzle.core.quantity.FundamentalTypeException.Reason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.BinaryOperator;

/**
 * An amount of a currency — a MONEY attribute's value. Money of different currencies is never combined or compared:
 * there are no exchange rates yet. The amount is kept normalized, like {@link Quantity}'s.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null || currency == null) {
            throw new FundamentalTypeException(Reason.INVALID_AMOUNT, "money needs an amount and a currency");
        }
        amount = Decimals.normalize(amount);
    }

    public static Money of(String amount, String currency) {
        return new Money(Decimals.parse(amount), currency);
    }

    /** {@code "100 EUR"} — an amount, white space, an ISO 4217 code of the catalog. */
    public static Money parse(String text, UnitCatalog catalog) {
        String[] parts = AmountAndCode.split(text);
        return new Money(Decimals.parse(parts[0]), catalog.currency(parts[1]).code());
    }

    public String amountText() {
        return Decimals.text(amount);
    }

    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    public Money plus(Money other) {
        return combine(other, BigDecimal::add);
    }

    public Money minus(Money other) {
        return combine(other, BigDecimal::subtract);
    }

    /** Scaled by a plain number, unrounded — {@link #rounded} when the result is final. */
    public Money times(BigDecimal multiplier) {
        return new Money(amount.multiply(multiplier), currency);
    }

    public Money dividedBy(BigDecimal divisor) {
        return new Money(Decimals.divide(amount, divisor), currency);
    }

    /** Rounded half up to the currency's minor units (EUR 2, JPY 0, KWD 3). */
    public Money rounded(UnitCatalog catalog) {
        return new Money(amount.setScale(catalog.currency(currency).minorUnits(), RoundingMode.HALF_UP), currency);
    }

    private Money combine(Money other, BinaryOperator<BigDecimal> operation) {
        requireSameCurrency(other);
        return new Money(operation.apply(amount, other.amount), currency);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new FundamentalTypeException(Reason.CURRENCY_MISMATCH, "%s and %s cannot be combined without an exchange rate".formatted(currency, other.currency));
        }
    }
}

package com.processpuzzle.core.quantity;

import com.processpuzzle.core.quantity.FundamentalTypeException.Reason;
import com.processpuzzle.core.quantity.UnitCatalog.UnitDefinition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.BinaryOperator;

/**
 * An amount of a unit — a QUANTITY attribute's value. The amount is kept normalized (no trailing zeros), so two quantities
 * that read the same are {@code equals}. Operations take the catalog that knows the units, usually {@link UnitCatalog#standard()}.
 */
public record Quantity(BigDecimal amount, String unit) {

    public Quantity {
        if (amount == null || unit == null) {
            throw new FundamentalTypeException(Reason.INVALID_AMOUNT, "a quantity needs an amount and a unit");
        }
        amount = Decimals.normalize(amount);
    }

    public static Quantity of(String amount, String unit) {
        return new Quantity(Decimals.parse(amount), unit);
    }

    /** {@code "5 kg"} — an amount, white space, a unit code of the catalog. */
    public static Quantity parse(String text, UnitCatalog catalog) {
        String[] parts = AmountAndCode.split(text);
        return new Quantity(Decimals.parse(parts[0]), catalog.unit(parts[1]).code());
    }

    public String amountText() {
        return Decimals.text(amount);
    }

    /** The value in its kind's base unit: {@code (amount + offset) × factor / divisor} — what is stored as {@code baseAmount}. */
    public BigDecimal baseAmount(UnitCatalog catalog) {
        UnitDefinition definition = catalog.unit(unit);
        return Decimals.normalize(Decimals.divide(amount.add(definition.offset()).multiply(definition.factor()), definition.divisor()));
    }

    /** The same measurement in another unit of its kind, with the single division the frontend makes too. */
    public Quantity convertTo(String toUnit, UnitCatalog catalog) {
        UnitDefinition from = catalog.unit(unit);
        UnitDefinition to = catalog.unit(toUnit);
        requireSameKind(from, to);
        if (from.code().equals(to.code())) {
            return this;
        }
        BigDecimal numerator = amount.add(from.offset()).multiply(from.factor()).multiply(to.divisor());
        BigDecimal denominator = from.divisor().multiply(to.factor());
        return new Quantity(Decimals.divide(numerator, denominator).subtract(to.offset()), to.code());
    }

    /** Negative, zero or positive as this measures less than, as much as or more than {@code other}. */
    public int compareTo(Quantity other, UnitCatalog catalog) {
        requireSameKind(catalog.unit(unit), catalog.unit(other.unit));
        return baseAmount(catalog).compareTo(other.baseAmount(catalog));
    }

    /** {@code this + other}, in this unit. Temperatures on an offset scale compare and convert but don't add. */
    public Quantity plus(Quantity other, UnitCatalog catalog) {
        return combine(other, catalog, BigDecimal::add);
    }

    public Quantity minus(Quantity other, UnitCatalog catalog) {
        return combine(other, catalog, BigDecimal::subtract);
    }

    public Quantity times(BigDecimal multiplier, UnitCatalog catalog) {
        requireNoOffset(catalog.unit(unit));
        return new Quantity(amount.multiply(multiplier), unit);
    }

    public Quantity dividedBy(BigDecimal divisor, UnitCatalog catalog) {
        requireNoOffset(catalog.unit(unit));
        return new Quantity(Decimals.divide(amount, divisor), unit);
    }

    /** The amount rounded half up to {@code scale} decimal places. */
    public Quantity rounded(int scale) {
        return new Quantity(amount.setScale(scale, RoundingMode.HALF_UP), unit);
    }

    private Quantity combine(Quantity other, UnitCatalog catalog, BinaryOperator<BigDecimal> operation) {
        UnitDefinition mine = catalog.unit(unit);
        UnitDefinition theirs = catalog.unit(other.unit);
        requireSameKind(mine, theirs);
        requireNoOffset(mine);
        requireNoOffset(theirs);
        return new Quantity(operation.apply(amount, other.convertTo(unit, catalog).amount), unit);
    }

    private static void requireSameKind(UnitDefinition a, UnitDefinition b) {
        if (!a.kind().equals(b.kind())) {
            throw new FundamentalTypeException(Reason.KIND_MISMATCH, "%s (%s) and %s (%s) measure different things".formatted(a.code(), a.kind(), b.code(), b.kind()));
        }
    }

    private static void requireNoOffset(UnitDefinition unit) {
        if (unit.hasOffset()) {
            throw new FundamentalTypeException(Reason.OFFSET_ARITHMETIC, "%s is an offset scale; convert it to its kind's base unit first".formatted(unit.code()));
        }
    }
}

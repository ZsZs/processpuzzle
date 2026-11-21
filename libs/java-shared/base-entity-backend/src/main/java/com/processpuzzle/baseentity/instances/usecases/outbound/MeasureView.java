package com.processpuzzle.baseentity.instances.usecases.outbound;

import java.util.List;

/**
 * What an instance write needs to know about a QUANTITY, MONEY or DIMENSIONS attribute — read-only, like
 * {@link EntityAttributeView} itself. Null lists and numbers mean the option is not set.
 *
 * @param kind              which control the attribute is
 * @param quantityKind      QUANTITY: the kind whose units are allowed when {@code allowedUnits} names none
 * @param allowedUnits      QUANTITY and DIMENSIONS: the unit codes a value may use
 * @param scale             QUANTITY and DIMENSIONS: the most decimal places an amount may have
 * @param allowedCurrencies MONEY: the ISO 4217 codes a value may use
 * @param dimensionCount    DIMENSIONS: 2 or 3; null means 2
 * @param defaultUnit       QUANTITY and DIMENSIONS: the unit a bare number written to the attribute is taken to be in
 * @param defaultCurrency   MONEY: the currency a bare number written to the attribute is taken to be in
 */
public record MeasureView(
    Kind kind,
    String quantityKind,
    List<String> allowedUnits,
    Integer scale,
    List<String> allowedCurrencies,
    Integer dimensionCount,
    String defaultUnit,
    String defaultCurrency
) {

    /** A measure without defaults, for a caller that only validates what is submitted. */
    public MeasureView(Kind kind, String quantityKind, List<String> allowedUnits, Integer scale, List<String> allowedCurrencies,
                       Integer dimensionCount) {
        this(kind, quantityKind, allowedUnits, scale, allowedCurrencies, dimensionCount, null, null);
    }

    /** The three controls whose values are measures. */
    public enum Kind {
        QUANTITY, MONEY, DIMENSIONS
    }

    public MeasureView {
        allowedUnits = allowedUnits == null ? null : List.copyOf(allowedUnits);
        allowedCurrencies = allowedCurrencies == null ? null : List.copyOf(allowedCurrencies);
    }

    /** True for a DIMENSIONS attribute of three axes. */
    public boolean threeDimensional() {
        return kind == Kind.DIMENSIONS && dimensionCount != null && dimensionCount == 3;
    }
}

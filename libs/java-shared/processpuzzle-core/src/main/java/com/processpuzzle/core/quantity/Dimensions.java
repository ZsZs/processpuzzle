package com.processpuzzle.core.quantity;

import com.processpuzzle.core.quantity.FundamentalTypeException.Reason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Two or three lengths in one unit — a DIMENSIONS attribute's value. {@code depth} is null for a 2D value. */
public record Dimensions(BigDecimal width, BigDecimal height, BigDecimal depth, String unit) {

    private static final String LENGTH = "LENGTH";

    public Dimensions {
        if (width == null || height == null || unit == null) {
            throw new FundamentalTypeException(Reason.INVALID_AMOUNT, "dimensions need a width, a height and a unit");
        }
        width = Decimals.normalize(width);
        height = Decimals.normalize(height);
        depth = depth == null ? null : Decimals.normalize(depth);
    }

    public boolean isThreeDimensional() {
        return depth != null;
    }

    /** {@code width × height} in the AREA base unit (m²) — what is stored as {@code baseArea}. */
    public Quantity area(UnitCatalog catalog) {
        List<BigDecimal> axes = baseAxes(catalog);
        return new Quantity(product(axes.subList(0, 2)), catalog.kind("AREA").baseUnit());
    }

    /** {@code width × height × depth} in the VOLUME base unit (m³) — what is stored as {@code baseVolume}. */
    public Quantity volume(UnitCatalog catalog) {
        if (!isThreeDimensional()) {
            throw new FundamentalTypeException(Reason.KIND_MISMATCH, "two-dimensional dimensions have no volume");
        }
        return new Quantity(product(baseAxes(catalog)), catalog.kind("VOLUME").baseUnit());
    }

    /** Every axis in another length unit. */
    public Dimensions convertTo(String toUnit, UnitCatalog catalog) {
        requireLength(catalog);
        return new Dimensions(axis(width, toUnit, catalog), axis(height, toUnit, catalog), depth == null ? null : axis(depth, toUnit, catalog), toUnit);
    }

    private BigDecimal axis(BigDecimal amount, String toUnit, UnitCatalog catalog) {
        return new Quantity(amount, unit).convertTo(toUnit, catalog).amount();
    }

    private List<BigDecimal> baseAxes(UnitCatalog catalog) {
        requireLength(catalog);
        List<BigDecimal> axes = new ArrayList<>(List.of(width, height));
        if (depth != null) {
            axes.add(depth);
        }
        return axes.stream().map(amount -> new Quantity(amount, unit).baseAmount(catalog)).toList();
    }

    /** Rounded like a division, so the frontend's product of the same base lengths has the same digits. */
    private static BigDecimal product(List<BigDecimal> factors) {
        return factors.stream().reduce(BigDecimal.ONE, BigDecimal::multiply).setScale(Decimals.DIVISION_PLACES, RoundingMode.HALF_UP);
    }

    private void requireLength(UnitCatalog catalog) {
        String kind = catalog.unit(unit).kind();
        if (!LENGTH.equals(kind)) {
            throw new FundamentalTypeException(Reason.KIND_MISMATCH, "dimensions are lengths, %s measures %s".formatted(unit, kind));
        }
    }
}

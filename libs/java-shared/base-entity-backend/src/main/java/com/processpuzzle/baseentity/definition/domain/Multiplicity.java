package com.processpuzzle.baseentity.definition.domain;

/**
 * How many values an attribute holds. The lower bound is 0 or 1; the upper bound is 1, a fixed maximum
 * ({@code x}, given by {@link BaseEntityAttribute#getMaxOccurs()}) or unbounded ({@code n}). An absent
 * multiplicity means a single value. Value counts are not validated yet — only the definition itself is.
 */
public enum Multiplicity {
    ZERO_TO_ONE("0..1", false),
    ZERO_TO_X("0..x", true),
    ZERO_TO_N("0..n", false),
    ONE_TO_X("1..x", true),
    ONE_TO_N("1..n", false);

    private final String notation;
    private final boolean boundedByMaxOccurs;

    Multiplicity(String notation, boolean boundedByMaxOccurs) {
        this.notation = notation;
        this.boundedByMaxOccurs = boundedByMaxOccurs;
    }

    public String notation() {
        return notation;
    }

    /** True for the {@code x} forms, whose upper bound is the attribute's maxOccurs. */
    public boolean isBoundedByMaxOccurs() {
        return boundedByMaxOccurs;
    }

    /** The upper bound; {@link Integer#MAX_VALUE} stands for unbounded. A missing maxOccurs counts as 1. */
    public int upperBound(Integer maxOccurs) {
        return switch (this) {
            case ZERO_TO_ONE -> 1;
            case ZERO_TO_X, ONE_TO_X -> maxOccurs == null ? 1 : maxOccurs;
            case ZERO_TO_N, ONE_TO_N -> Integer.MAX_VALUE;
        };
    }

    public boolean isMultiValued(Integer maxOccurs) {
        return upperBound(maxOccurs) > 1;
    }

    /** Null-safe variant: an absent multiplicity is single-valued. */
    public static boolean isMultiValued(Multiplicity multiplicity, Integer maxOccurs) {
        return multiplicity != null && multiplicity.isMultiValued(maxOccurs);
    }
}

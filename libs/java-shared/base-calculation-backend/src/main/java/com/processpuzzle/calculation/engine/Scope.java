package com.processpuzzle.calculation.engine;

/**
 * Where a decision is evaluated: once ({@link Type#SCALAR}), once per row of an input ({@link Type#ROW}), or once
 * per distinct value of an attribute of an input's rows ({@link Type#GROUP}).
 */
public record Scope(Type type, String input, String attribute) {

    public enum Type {
        SCALAR, ROW, GROUP
    }

    public static final Scope SCALAR = new Scope(Type.SCALAR, null, null);

    public static Scope row(String input) {
        return new Scope(Type.ROW, input, null);
    }

    public static Scope group(String input, String attribute) {
        return new Scope(Type.GROUP, input, attribute);
    }

    public boolean isScalar() {
        return type == Type.SCALAR;
    }

    /** {@code scalar}, {@code row:<input>} or {@code group:<attribute>} — what a trace and a graph show. */
    @Override
    public String toString() {
        return switch (type) {
            case SCALAR -> "scalar";
            case ROW -> "row:" + input;
            case GROUP -> "group:" + attribute;
        };
    }
}

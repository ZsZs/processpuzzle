package com.processpuzzle.calculation.engine;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The value semantics the engine relies on outside FEEL: ordering for ranks and min/max, numbers for sums, equality
 * for distinct counts and partitions, and the plain JSON shape a result is stored and answered in.
 */
public final class Values {

    /** Numbers by value, then any two values of one comparable type; nulls last. */
    public static final Comparator<Object> ORDER = Values::compare;

    private Values() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compare(Object left, Object right) {
        if (left == null) {
            return right == null ? 0 : 1;
        }
        if (right == null) {
            return -1;
        }
        BigDecimal leftNumber = number(left);
        BigDecimal rightNumber = number(right);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber);
        }
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
            return comparable.compareTo(right);
        }
        return left.toString().compareTo(right.toString());
    }

    /** The value as a {@link BigDecimal}, or null when it is no number. */
    public static BigDecimal number(Object value) {
        return switch (value) {
            case BigDecimal decimal -> decimal;
            case Integer integer -> BigDecimal.valueOf(integer);
            case Long longValue -> BigDecimal.valueOf(longValue);
            case Short shortValue -> BigDecimal.valueOf(shortValue);
            case Byte byteValue -> BigDecimal.valueOf(byteValue);
            case java.math.BigInteger integer -> new BigDecimal(integer);
            // Through the shortest decimal representation, so 19.99 stays 19.99 rather than its binary neighbour.
            case Double doubleValue -> doubleValue.isNaN() || doubleValue.isInfinite() ? null : BigDecimal.valueOf(doubleValue);
            case Float floatValue -> floatValue.isNaN() || floatValue.isInfinite() ? null : new BigDecimal(floatValue.toString());
            case null, default -> null;
        };
    }

    /** A key equal for equal values — {@code 1} and {@code 1.0} included — for distinct counts and partitions. */
    public static Object key(Object value) {
        BigDecimal number = number(value);
        if (number != null) {
            return number.signum() == 0 ? BigDecimal.ZERO : number.stripTrailingZeros();
        }
        return value;
    }

    /**
     * The value in the shape stored as JSON and answered by the API: numbers stay numbers, dates, times and
     * durations become their ISO text, and a FEEL function becomes a placeholder.
     */
    public static Object plain(Object value) {
        return switch (value) {
            case null -> null;
            case BigDecimal decimal -> decimal;
            case Number number -> number(number) == null ? number.toString() : number(number);
            case Boolean bool -> bool;
            case String text -> text;
            case Duration duration -> duration.toString();
            case Period period -> period.toString();
            case TemporalAccessor temporal -> temporal.toString();
            case Map<?, ?> map -> plainMap(map);
            case Collection<?> collection -> plainList(collection);
            default -> value.getClass().getName().startsWith("org.camunda.feel") ? "<function>" : value.toString();
        };
    }

    public static Map<String, Object> plainMap(Map<?, ?> map) {
        Map<String, Object> plain = new LinkedHashMap<>();
        map.forEach((key, value) -> plain.put(String.valueOf(key), plain(value)));
        return plain;
    }

    public static List<Object> plainList(Collection<?> collection) {
        List<Object> plain = new ArrayList<>();
        collection.forEach(value -> plain.add(plain(value)));
        return plain;
    }
}

package com.processpuzzle.baseentity.instances.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** The values of a natural key in a payload, in a form that compares equal for equal values however they were typed in. */
final class NaturalKey {

    private NaturalKey() {
    }

    /** The values of {@code attributes} in the payload; empty when any of them is missing or blank, which leaves the key unconstrained. */
    static Optional<List<String>> valuesOf(Map<String, Object> payload, List<String> attributes) {
        List<String> values = new ArrayList<>();
        for (String attribute : attributes) {
            String value = payload == null ? null : normalized(payload.get(attribute));
            if (value == null) {
                return Optional.empty();
            }
            values.add(value);
        }
        return Optional.of(values);
    }

    static String describe(List<String> attributes, List<String> values) {
        return java.util.stream.IntStream.range(0, attributes.size())
            .mapToObj(index -> attributes.get(index) + " = '" + values.get(index) + "'")
            .collect(Collectors.joining(", "));
    }

    private static String normalized(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}

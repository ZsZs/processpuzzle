package com.processpuzzle.rule.usecase.assist;

import com.processpuzzle.rule.port.RuleContext;
import com.processpuzzle.rule.port.RuleContext.ContextAttribute;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Objects built from an entity's description alone, to run a candidate rule against: one with every attribute filled,
 * one with only the required ones, one with everything zero or empty. They are made of type defaults, never of an
 * organization's data, so nothing of the data reaches the model or the dry run.
 */
final class SampleEntities {

    private static final String DATE = "2025-01-15";
    private static final String DATE_TIME = "2025-01-15T10:00:00Z";

    private SampleEntities() {
    }

    /**
     * One sample object.
     *
     * @param name      what the sample is, for a message
     * @param data      the object as a rule's {@code entity} reads it
     * @param unsampled the attributes it could not give a value, because the description does not say what a value looks like
     */
    record Sample(String name, Map<String, Object> data, Set<String> unsampled) {
        static final String FILLED = "an object with every attribute filled";
    }

    static List<Sample> of(RuleContext context) {
        List<Sample> samples = new ArrayList<>();
        for (Mode mode : Mode.values()) {
            Set<String> unsampled = new LinkedHashSet<>();
            Map<String, Object> data = object(context.attributes(), mode, unsampled);
            samples.add(new Sample(mode.label, data, unsampled));
        }
        return samples;
    }

    private enum Mode {
        FILLED(Sample.FILLED), REQUIRED_ONLY("an object with only its required attributes"), ZERO("an object with zeros and empty values");

        private final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private static Map<String, Object> object(List<ContextAttribute> attributes, Mode mode, Set<String> unsampled) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (ContextAttribute attribute : attributes) {
            if (mode == Mode.REQUIRED_ONLY && !attribute.required()) {
                continue;
            }
            Object value = value(attribute, mode, unsampled);
            if (value == null) {
                unsampled.add(attribute.name());
            } else {
                data.put(attribute.name(), value);
            }
        }
        return data;
    }

    private static Object value(ContextAttribute attribute, Mode mode, Set<String> unsampled) {
        if (attribute.multiValued()) {
            return mode == Mode.ZERO ? new ArrayList<>() : listOf(single(attribute, mode, unsampled));
        }
        return single(attribute, mode, unsampled);
    }

    private static List<Object> listOf(Object item) {
        List<Object> list = new ArrayList<>();
        if (item != null) {
            list.add(item);
        }
        return list;
    }

    private static Object single(ContextAttribute attribute, Mode mode, Set<String> unsampled) {
        boolean zero = mode == Mode.ZERO;
        if (attribute.measure() != null) {
            return measure(attribute, zero);
        }
        if (attribute.embedded()) {
            return object(attribute.children(), mode, unsampled);
        }
        return switch (attribute.type()) {
            case "NUMBER" -> zero ? 0 : 1;
            case "BOOLEAN" -> !zero;
            case "DATE" -> DATE;
            case "DATE_TIME" -> DATE_TIME;
            case "TEXT", "ENUM", "REFERENCE" -> zero ? "" : "sample";
            default -> null;
        };
    }

    /** A measured value as it is stored; null when the attribute names no unit or currency to give it. */
    private static Object measure(ContextAttribute attribute, boolean zero) {
        if (attribute.measureOptions().isEmpty()) {
            return null;
        }
        String unit = attribute.measureOptions().get(0);
        String amount = zero ? "0" : "1";
        Map<String, Object> value = new LinkedHashMap<>();
        switch (attribute.measure()) {
            case "MONEY" -> {
                value.put("amount", amount);
                value.put("currency", unit);
            }
            case "DIMENSIONS" -> {
                value.put("width", amount);
                value.put("height", amount);
                value.put("unit", unit);
            }
            default -> {
                value.put("amount", amount);
                value.put("unit", unit);
            }
        }
        return value;
    }
}

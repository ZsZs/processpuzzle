package com.processpuzzle.baseentity.instances.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Plans a batch of records to load: it reads the references each payload makes, orders the records so that every
 * record comes after the ones it refers to, and marks the records that cannot be loaded at all (a malformed
 * reference, or a reference cycle). It touches no storage; {@code LoadRecordsUseCase} does the writing.
 *
 * <p>A payload refers to a record by a top-level attribute whose value is {@code {"$ref": {"entity": <code>,
 * "key": {<attribute>: <value>, ...}}}}, or a list of such values. The key names the referenced record by its
 * attribute values, so a data file never needs the database ids. The referenced record is either another record of
 * the same batch or one already stored.
 */
public final class RecordPlanner {

    /** The payload key that marks a reference. */
    public static final String REF = "$ref";

    private static final String ENTITY = "entity";
    private static final String KEY = "key";

    /** One record of a batch: the definition it is of, and its payload as written. */
    public record RecordInput(String code, Map<String, Object> payload) {
    }

    /** A reference: the entity it points into and the attribute values that name the record. */
    public record Ref(String entity, Map<String, Object> key) {
    }

    /**
     * @param order      every index of the batch, each after the records it refers to; a record that cannot be loaded
     *                   stays in it, so that the report covers every record
     * @param cannotLoad the reason each record that cannot be loaded is refused, by index
     */
    public record Ordering(List<Integer> order, Map<Integer, String> cannotLoad) {
    }

    private RecordPlanner() {
    }

    /** Orders the batch. A record that cannot be ordered is reported in {@code cannotLoad}, not thrown. */
    public static Ordering order(List<RecordInput> records) {
        int size = records.size();
        Map<Integer, String> cannotLoad = new LinkedHashMap<>();
        List<Set<Integer>> dependencies = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            Set<Integer> targets = new TreeSet<>();
            try {
                for (Ref ref : refsOf(records.get(index).payload())) {
                    findTarget(records, ref).ifPresent(targets::add);
                }
            } catch (IllegalArgumentException e) {
                cannotLoad.put(index, e.getMessage());
            }
            dependencies.add(targets);
        }
        List<Integer> order = placeAfterDependencies(size, dependencies);
        for (int index = 0; index < size; index++) {
            if (!order.contains(index)) {
                cannotLoad.putIfAbsent(index, "refers to records that refer back to it: a reference cycle");
                order.add(index);
            }
        }
        return new Ordering(order, cannotLoad);
    }

    /**
     * The batch record a reference points to, or empty when the batch holds none that matches it. Matching compares the
     * values of the reference's key attributes, normalized the way the natural keys are.
     */
    public static Optional<Integer> findTarget(List<RecordInput> records, Ref ref) {
        for (int index = 0; index < records.size(); index++) {
            RecordInput candidate = records.get(index);
            if (candidate.code().equals(ref.entity()) && matches(candidate.payload(), ref)) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    /** Whether the payload's values for the reference's key attributes equal the key's. */
    public static boolean matches(Map<String, Object> payload, Ref ref) {
        List<String> attributes = List.copyOf(new TreeSet<>(ref.key().keySet()));
        if (attributes.isEmpty()) {
            return false;
        }
        Optional<List<String>> wanted = NaturalKey.valuesOf(ref.key(), attributes);
        return wanted.isPresent() && wanted.equals(NaturalKey.valuesOf(payload, attributes));
    }

    /**
     * Whether two payloads carry the same values for every attribute of one natural key. A payload without a value for one
     * of the attributes is not constrained by that key, so it is never the same.
     */
    public static boolean sameKey(Map<String, Object> first, Map<String, Object> second, List<String> attributes) {
        Optional<List<String>> values = NaturalKey.valuesOf(first, attributes);
        return values.isPresent() && values.equals(NaturalKey.valuesOf(second, attributes));
    }

    /**
     * The references of a payload, one per reference value. A value marked {@code $ref} that is not a well-formed reference
     * is refused rather than stored as text.
     *
     * @throws IllegalArgumentException for a malformed reference
     */
    public static List<Ref> refsOf(Map<String, Object> payload) {
        List<Ref> refs = new ArrayList<>();
        if (payload == null) {
            return refs;
        }
        for (Object value : payload.values()) {
            if (value instanceof List<?> items) {
                for (Object item : items) {
                    if (isRef(item)) {
                        refs.add(parse(item));
                    }
                }
            } else if (isRef(value)) {
                refs.add(parse(value));
            }
        }
        return refs;
    }

    /**
     * The payload with every reference value replaced by what {@code resolver} gives for it; a list keeps its order. Any
     * other value is copied as it is.
     */
    public static Map<String, Object> resolve(Map<String, Object> payload, Function<Ref, String> resolver) {
        Map<String, Object> resolved = new HashMap<>();
        payload.forEach((attribute, value) -> {
            if (value instanceof List<?> items && items.stream().anyMatch(RecordPlanner::isRef)) {
                List<Object> replaced = new ArrayList<>();
                for (Object item : items) {
                    replaced.add(isRef(item) ? resolver.apply(parse(item)) : item);
                }
                resolved.put(attribute, replaced);
            } else if (isRef(value)) {
                resolved.put(attribute, resolver.apply(parse(value)));
            } else {
                resolved.put(attribute, value);
            }
        });
        return resolved;
    }

    /** Kahn's algorithm, first-come first-served among the records that are free to go. Cycle members are left out. */
    private static List<Integer> placeAfterDependencies(int size, List<Set<Integer>> dependencies) {
        Map<Integer, List<Integer>> dependents = new HashMap<>();
        int[] pending = new int[size];
        for (int index = 0; index < size; index++) {
            pending[index] = dependencies.get(index).size();
            for (int target : dependencies.get(index)) {
                dependents.computeIfAbsent(target, key -> new ArrayList<>()).add(index);
            }
        }
        Deque<Integer> ready = new ArrayDeque<>();
        for (int index = 0; index < size; index++) {
            if (pending[index] == 0) {
                ready.add(index);
            }
        }
        List<Integer> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            int index = ready.poll();
            order.add(index);
            for (int dependent : dependents.getOrDefault(index, List.of())) {
                if (--pending[dependent] == 0) {
                    ready.add(dependent);
                }
            }
        }
        return order;
    }

    private static boolean isRef(Object value) {
        return value instanceof Map<?, ?> map && map.containsKey(REF);
    }

    private static Ref parse(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.size() != 1 || !(map.get(REF) instanceof Map<?, ?> body)) {
            throw new IllegalArgumentException("A reference must be {\"" + REF + "\": {\"" + ENTITY + "\": ..., \"" + KEY + "\": {...}}}.");
        }
        if (!(body.get(ENTITY) instanceof String entity) || entity.isBlank()) {
            throw new IllegalArgumentException("A reference names no entity.");
        }
        if (!(body.get(KEY) instanceof Map<?, ?> key) || key.isEmpty()) {
            throw new IllegalArgumentException("A reference to '" + entity + "' names no key.");
        }
        Map<String, Object> keyValues = new HashMap<>();
        key.forEach((attribute, keyValue) -> keyValues.put(Objects.toString(attribute), keyValue));
        return new Ref(entity, keyValues);
    }
}

package com.processpuzzle.core.definition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A fingerprint is only useful if "the same definition" always writes the same string. These specs pin
 * the three things that make that true: key order never matters, nulls never appear, and the named
 * volatile fields are gone wherever they hide.
 */
class CanonicalJsonTest {

    @Test
    void insertionOrderDoesNotChangeTheString() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", 1);
        first.put("c", "x");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("c", "x");
        second.put("a", 1);
        second.put("b", 2);

        assertThat(CanonicalJson.of(first, Set.of())).isEqualTo(CanonicalJson.of(second, Set.of()));
        assertThat(CanonicalJson.of(first, Set.of())).isEqualTo("{\"a\":1,\"b\":2,\"c\":\"x\"}");
    }

    @Test
    void nestedObjectsAreSortedToo() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("z", 1);
        inner.put("y", 2);
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("nested", inner);
        outer.put("list", List.of(inner));
        outer.put("first", true);

        assertThat(CanonicalJson.of(outer, Set.of()))
                .isEqualTo("{\"first\":true,\"list\":[{\"y\":2,\"z\":1}],\"nested\":{\"y\":2,\"z\":1}}");
    }

    @Test
    void beanPropertiesAreSortedAlphabetically() {
        assertThat(CanonicalJson.of(new Bean("n", 3, null), Set.of())).isEqualTo("{\"name\":\"n\",\"size\":3}");
    }

    @Test
    void nullsAreOmittedAtEveryDepth() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("kept", "v");
        inner.put("gone", null);
        Map<String, Object> outer = new HashMap<>();
        outer.put("inner", inner);
        outer.put("missing", null);

        assertThat(CanonicalJson.of(outer, Set.of())).isEqualTo("{\"inner\":{\"kept\":\"v\"}}");
    }

    @Test
    void arrayOrderIsPreserved() {
        assertThat(CanonicalJson.of(List.of(3, 1, 2), Set.of())).isEqualTo("[3,1,2]");
    }

    @Test
    void volatileFieldsAreStrippedInNestedObjectsAndArrays() {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("id", "c-1");
        child.put("name", "child");
        child.put("version", 7);
        List<Object> children = new ArrayList<>(Arrays.asList(child, Map.of("id", "c-2", "name", "other")));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", "r-1");
        root.put("version", 3);
        root.put("createdAt", "2026-10-05T10:00:00Z");
        root.put("name", "root");
        root.put("nested", Map.of("deep", Map.of("id", "d-1", "kept", 1)));
        root.put("children", children);

        String json = CanonicalJson.of(root, Set.of("id", "version", "createdAt"));

        assertThat(json).isEqualTo("{\"children\":[{\"name\":\"child\"},{\"name\":\"other\"}],"
                + "\"name\":\"root\",\"nested\":{\"deep\":{\"kept\":1}}}");
    }

    @Test
    void definitionsDifferingOnlyInVolatileFieldsAreEqual() {
        Set<String> volatileFields = Set.of("id", "version");

        String one = CanonicalJson.of(Map.of("id", "a", "version", 1, "name", "Order"), volatileFields);
        String two = CanonicalJson.of(Map.of("id", "b", "version", 9, "name", "Order"), volatileFields);
        String three = CanonicalJson.of(Map.of("id", "a", "version", 1, "name", "Invoice"), volatileFields);

        assertThat(one).isEqualTo(two);
        assertThat(one).isNotEqualTo(three);
    }

    @Test
    void aFieldIsStrippedOnlyByItsExactName() {
        assertThat(CanonicalJson.of(Map.of("id", 1, "entityId", 2), Set.of("id"))).isEqualTo("{\"entityId\":2}");
    }

    private record Bean(String name, int size, String note) {
    }
}

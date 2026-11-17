package com.processpuzzle.core.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class DefinitionYamlTest {

    private static final byte[] FILE = ("""
            version: 2
            rules:
              - id: a
                name: A
              - id: b
                name: B
              - name: nameless
            """).getBytes(StandardCharsets.UTF_8);

    @Test
    void readsTheKeysOfAFileInOrder_skippingEntriesWithoutOne() {
        assertThat(DefinitionYaml.keys(FILE, "rules", "id")).containsExactly("a", "b");
        assertThat(DefinitionYaml.keys("".getBytes(StandardCharsets.UTF_8), "rules", "id")).isEmpty();
    }

    @Test
    void narrowsAFileToTheKeysGiven_keepingTheOtherFieldsAndKeylessEntries() {
        String narrowed = new String(DefinitionYaml.only(FILE, "rules", "id", Set.of("b")), StandardCharsets.UTF_8);

        assertThat(narrowed).contains("version").contains("name: \"B\"").contains("nameless").doesNotContain("name: \"A\"");
        assertThat(DefinitionYaml.keys(narrowed.getBytes(StandardCharsets.UTF_8), "rules", "id")).containsExactly("b");
    }

    @Test
    void aFileWithoutTheListIsLeftAsItIs() {
        byte[] other = "something: 1\n".getBytes(StandardCharsets.UTF_8);

        assertThat(DefinitionYaml.only(other, "rules", "id", Set.of("a"))).isEqualTo(other);
    }

    @Test
    void removalRetriesWhatBecomesFree_andKeepsTheReasonOfWhatDoesNot() {
        java.util.List<String> gone = new java.util.ArrayList<>();
        Consumer<String> delete = key -> {
            if (key.equals("parent") && !gone.contains("child")) {
                throw new IllegalStateException("still the parent of child");
            }
            if (key.equals("stuck")) {
                throw new IllegalStateException("has data");
            }
            gone.add(key);
        };

        DefinitionRemoval removal = DefinitionRemoval.removing(List.of("parent", "child", "stuck"), delete);

        assertThat(removal.removed()).containsExactly("child", "parent");
        assertThat(removal.refused()).containsOnlyKeys("stuck").containsEntry("stuck", "has data");
    }
}

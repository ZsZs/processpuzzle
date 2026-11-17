package com.processpuzzle.starter.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MigrationFileTest {

    private static final String PATH = "migrations/1.0.0-to-1.1.0.yaml";

    private static byte[] bytes(String yaml) {
        return yaml.getBytes(StandardCharsets.UTF_8);
    }

    private static String file(String versions, String steps) {
        return "migration:\n" + versions + "  steps:\n" + steps;
    }

    @Test
    void readsTheStepsInOrder_numberingThoseWithoutAnId() {
        MigrationFile file = MigrationFile.read(PATH, bytes(file("  from: 1.0.0\n  to: 1.1.0\n", """
                    - kind: entity
                      key: customer
                      operation: renameAttribute
                      subject: customerName
                      parameters: { to: name }
                    - id: fill
                      kind: entity
                      key: customer
                      operation: fillAttribute
                      subject: segment
                      parameters: { value: standard }
                """)));

        assertThat(file.from()).isEqualTo("1.0.0");
        assertThat(file.to()).isEqualTo("1.1.0");
        assertThat(file.steps()).extracting(step -> step.id() + ":" + step.operation()).containsExactly("step-1:renameAttribute", "fill:fillAttribute");
        assertThat(file.steps().getFirst().parameters()).containsEntry("to", "name");
    }

    @Test
    void aFileWithNoStepsIsAMigrationThatChangesNothing() {
        assertThat(MigrationFile.read(PATH, bytes("migration:\n  from: 1.0.0\n  to: 1.1.0\n")).steps()).isEmpty();
    }

    @Test
    void theNameTellsTheVersions() {
        assertThat(MigrationFile.versionsOf(PATH)).hasValueSatisfying(versions -> assertThat(versions).containsExactly("1.0.0", "1.1.0"));
        assertThat(MigrationFile.versionsOf("migrations/1.0.0-rc1-to-1.0.0.yaml")).hasValueSatisfying(
                versions -> assertThat(versions).containsExactly("1.0.0-rc1", "1.0.0"));
        assertThat(MigrationFile.pathOf("1.0.0", "1.1.0")).isEqualTo(PATH);
        assertThat(MigrationFile.versionsOf("migrations/notes.yaml")).isEmpty();
        assertThat(MigrationFile.versionsOf("other/1.0.0-to-1.1.0.yaml")).isEmpty();
        assertThat(MigrationFile.versionsOf("migrations/sub/1.0.0-to-1.1.0.yaml")).isEmpty();
        assertThat(MigrationFile.versionsOf("migrations/-to-1.1.0.yaml")).isEmpty();
        assertThat(MigrationFile.versionsOf(null)).isEmpty();
    }

    @Test
    void refusesWhatIsNotAMigration() {
        byte[] ok = bytes("migration:\n  from: 1.0.0\n  to: 1.1.0\n");
        assertThatThrownBy(() -> MigrationFile.read("migrations/notes.yaml", ok)).hasMessageContaining("migrations/<from>-to-<to>.yaml");
        byte[] broken = bytes("migration: [");
        assertThatThrownBy(() -> MigrationFile.read(PATH, broken)).hasMessageContaining("not a readable migration");
        byte[] empty = bytes("something: 1\n");
        assertThatThrownBy(() -> MigrationFile.read(PATH, empty)).hasMessageContaining("'migration:' block");
        byte[] other = bytes(file("  from: 1.0.0\n  to: 1.2.0\n", ""));
        assertThatThrownBy(() -> MigrationFile.read(PATH, other)).hasMessageContaining("named for 1.0.0 → 1.1.0");
        byte[] backwards = bytes("migration:\n  from: 1.1.0\n  to: 1.0.0\n");
        assertThatThrownBy(() -> MigrationFile.read("migrations/1.1.0-to-1.0.0.yaml", backwards)).hasMessageContaining("newer version");
    }

    @Test
    void aStepNeedsAKindAKeyAndAnOperation() {
        byte[] incomplete = bytes(file("  from: 1.0.0\n  to: 1.1.0\n", "    - kind: entity\n      key: customer\n"));

        assertThatThrownBy(() -> MigrationFile.read(PATH, incomplete)).hasMessageContaining("step 1 needs a kind, a key and an operation");
    }
}

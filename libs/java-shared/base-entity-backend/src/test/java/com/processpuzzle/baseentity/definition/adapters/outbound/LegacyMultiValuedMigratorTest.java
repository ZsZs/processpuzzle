package com.processpuzzle.baseentity.definition.adapters.outbound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class LegacyMultiValuedMigratorTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private LegacyMultiValuedMigrator migrator;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        jdbc = new JdbcTemplate(database);
        migrator = new LegacyMultiValuedMigrator(jdbc);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void migrate_legacyColumn_copiesTrueAsZeroToNAndDropsTheColumn() {
        jdbc.execute("CREATE TABLE base_entity_attribute (code VARCHAR(64), multiplicity VARCHAR(16), "
            + "is_multi_valued BOOLEAN NOT NULL)");
        jdbc.update("INSERT INTO base_entity_attribute VALUES ('lines', NULL, TRUE)");
        jdbc.update("INSERT INTO base_entity_attribute VALUES ('name', NULL, FALSE)");
        jdbc.update("INSERT INTO base_entity_attribute VALUES ('tags', 'ONE_TO_N', TRUE)");

        migrator.migrate();

        assertThat(migrator.legacyColumnExists()).isFalse();
        List<String> multiplicities = jdbc.queryForList(
            "SELECT COALESCE(multiplicity, '-') FROM base_entity_attribute ORDER BY code", String.class);
        assertThat(multiplicities).containsExactly("ZERO_TO_N", "-", "ONE_TO_N");
    }

    @Test
    void migrate_withoutLegacyColumn_isANoOp() {
        jdbc.execute("CREATE TABLE base_entity_attribute (code VARCHAR(64), multiplicity VARCHAR(16))");
        jdbc.update("INSERT INTO base_entity_attribute VALUES ('lines', 'ZERO_TO_N')");

        migrator.migrate();
        migrator.migrate();

        assertThat(jdbc.queryForObject("SELECT multiplicity FROM base_entity_attribute", String.class))
            .isEqualTo("ZERO_TO_N");
    }

    @Test
    void migrate_failure_doesNotFailStartup() {
        jdbc.execute("CREATE TABLE base_entity_attribute (code VARCHAR(64), is_multi_valued BOOLEAN NOT NULL)");

        assertThatNoException().isThrownBy(migrator::migrate);
        assertThat(migrator.legacyColumnExists()).isTrue();
    }
}

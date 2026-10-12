package com.processpuzzle.core.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ChannelBackfillTest {

    private JdbcTemplate jdbc;
    private ChannelBackfill backfill;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        backfill = new ChannelBackfill(jdbc, new DataSourceTransactionManager(dataSource));
    }

    @Test
    void migrate_turnsEveryRowIntoADraftWithAReleasedCopy() {
        legacyTable();

        assertThat(backfill.migrate("modules", List.of("org_key", "module_key"))).isTrue();

        assertThat(jdbc.queryForList("SELECT module_key, name, channel FROM modules ORDER BY module_key, channel"))
                .extracting(row -> row.get("MODULE_KEY") + "/" + row.get("NAME") + "/" + row.get("CHANNEL"))
                .containsExactly("a/A/DRAFT", "a/A/RELEASED", "b/B/DRAFT", "b/B/RELEASED");
    }

    @Test
    void migrate_widensThePrimaryKeyToTheChannel() {
        legacyTable();
        backfill.migrate("modules", List.of("org_key", "module_key"));

        assertThat(backfill.shapeOf("modules").orElseThrow().primaryKeyColumns())
                .containsExactlyInAnyOrder("org_key", "module_key", "channel");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO modules (org_key, module_key, name, channel) VALUES ('org', 'a', 'X', 'DRAFT')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void migrate_keepsAColumnHibernateAlreadyAdded() {
        legacyTable();
        jdbc.execute("ALTER TABLE modules ADD COLUMN channel VARCHAR(16) DEFAULT 'DRAFT' NOT NULL");

        assertThat(backfill.migrate("modules", List.of("org_key", "module_key"))).isTrue();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM modules WHERE channel = 'RELEASED'", Integer.class)).isEqualTo(2);
    }

    @Test
    void migrate_isIdempotent() {
        legacyTable();
        backfill.migrate("modules", List.of("org_key", "module_key"));

        assertThat(backfill.migrate("modules", List.of("org_key", "module_key"))).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM modules", Integer.class)).isEqualTo(4);
    }

    @Test
    void migrate_leavesAMissingTableAlone() {
        assertThat(backfill.migrate("modules", List.of("org_key", "module_key"))).isFalse();
    }

    @Test
    void migrate_refusesAKeyColumnThatIsNotAPlainName_andLeavesTheTableAlone() {
        legacyTable();

        assertThatThrownBy(() -> backfill.migrate("modules", List.of("org_key) --"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(backfill.shapeOf("modules").orElseThrow().primaryKeyColumns()).containsExactlyInAnyOrder("org_key", "module_key");
    }

    @Test
    void identifier_acceptsOnlyAPlainName() {
        assertThat(ChannelBackfill.identifier("app_modules")).isEqualTo("app_modules");
        assertThatThrownBy(() -> ChannelBackfill.identifier("modules; DROP TABLE x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChannelBackfill.identifier(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private void legacyTable() {
        jdbc.execute("CREATE TABLE modules (org_key VARCHAR(63) NOT NULL, module_key VARCHAR(100) NOT NULL, "
                + "name VARCHAR(200), PRIMARY KEY (org_key, module_key))");
        jdbc.update("INSERT INTO modules (org_key, module_key, name) VALUES ('org', 'a', 'A'), ('org', 'b', 'B')");
    }
}

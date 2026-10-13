package com.processpuzzle.rule.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * The tables as they stood before channels — the rules keyed on {@code (org_key, id)}, their fields joined on it and
 * held to it by a foreign key — moved onto channels. The foreign key is what a plain {@code ChannelBackfill} would trip
 * over: the database refuses to drop the primary key it depends on.
 */
class RuleChannelBackfillTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private RuleChannelBackfill backfill;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:rule-backfill;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE rule_definitions (org_key VARCHAR(63) NOT NULL, id VARCHAR(100) NOT NULL, "
                + "expression VARCHAR(4000) NOT NULL, CONSTRAINT rule_definitions_pkey PRIMARY KEY (id, org_key))");
        jdbc.execute("CREATE TABLE rule_definition_fields (rule_id VARCHAR(100) NOT NULL, org_key VARCHAR(63) NOT NULL, "
                + "field_name VARCHAR(100), CONSTRAINT fk_before_channels FOREIGN KEY (rule_id, org_key) "
                + "REFERENCES rule_definitions (id, org_key))");
        jdbc.update("INSERT INTO rule_definitions (org_key, id, expression) VALUES ('my-org', 'max-quantity', 'true')");
        jdbc.update("INSERT INTO rule_definition_fields (rule_id, org_key, field_name) VALUES ('max-quantity', 'my-org', 'quantity')");
        backfill = new RuleChannelBackfill(jdbc, new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void movesTheRulesAndTheirFieldsOntoChannels_once() {
        assertThat(backfill.migrateOnce()).isTrue();

        assertThat(jdbc.queryForList("SELECT id, channel FROM rule_definitions ORDER BY channel"))
                .extracting(row -> row.get("CHANNEL")).containsExactly("DRAFT", "RELEASED");
        assertThat(jdbc.queryForList("SELECT field_name, channel FROM rule_definition_fields ORDER BY channel"))
                .extracting(row -> List.of(row.get("FIELD_NAME"), row.get("CHANNEL")))
                .containsExactly(List.of("quantity", "DRAFT"), List.of("quantity", "RELEASED"));
        assertThat(backfill.oldForeignKeys()).isEmpty();
        assertThat(primaryKeyColumns()).isEqualTo(RuleChannelBackfill.KEY_IN_HIBERNATE_ORDER);
        addForeignKeyAsHibernateDoes();

        assertThat(backfill.migrateOnce()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rule_definition_fields", Integer.class)).isEqualTo(2);
    }

    /**
     * A database migrated before the key's order was known — stage, 2026-10-13: the key widened to
     * {@code (org_key, id, channel)}, the fields' new foreign key never created, because the database paired
     * {@code channel} with {@code org_key}. The next start rebuilds the key, and Hibernate's foreign key then fits.
     */
    @Test
    void rebuildsAKeyWidenedInTheOrderTheForeignKeyCannotReferTo() {
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE rule_definitions (org_key VARCHAR(63) NOT NULL, id VARCHAR(100) NOT NULL, "
                + "channel VARCHAR(16) NOT NULL, expression VARCHAR(4000) NOT NULL, "
                + "CONSTRAINT rule_definitions_pkey PRIMARY KEY (org_key, id, channel))");
        jdbc.execute("CREATE TABLE rule_definition_fields (rule_id VARCHAR(100) NOT NULL, org_key VARCHAR(63) NOT NULL, "
                + "channel VARCHAR(16) NOT NULL, field_name VARCHAR(100))");
        jdbc.update("INSERT INTO rule_definitions VALUES ('my-org', 'max-quantity', 'DRAFT', 'true'), ('my-org', 'max-quantity', 'RELEASED', 'true')");
        jdbc.update("INSERT INTO rule_definition_fields VALUES ('max-quantity', 'my-org', 'DRAFT', 'quantity'), ('max-quantity', 'my-org', 'RELEASED', 'quantity')");
        assertThatThrownBy(this::addForeignKeyAsHibernateDoes).isInstanceOf(DataAccessException.class);

        assertThat(backfill.migrateOnce()).isTrue();

        assertThat(primaryKeyColumns()).isEqualTo(RuleChannelBackfill.KEY_IN_HIBERNATE_ORDER);
        addForeignKeyAsHibernateDoes();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rule_definitions", Integer.class)).isEqualTo(2);
        assertThat(backfill.migrateOnce()).isFalse();
    }

    @Test
    void leavesAFieldsTableThatAlreadyHasItsChannelColumnIntact() {
        jdbc.execute("ALTER TABLE rule_definition_fields ADD COLUMN channel VARCHAR(16) DEFAULT 'DRAFT' NOT NULL");

        assertThat(backfill.migrateOnce()).isTrue();

        List<Map<String, Object>> fields = jdbc.queryForList("SELECT channel FROM rule_definition_fields ORDER BY channel");
        assertThat(fields).extracting(row -> row.get("CHANNEL")).containsExactly("DRAFT", "RELEASED");
    }

    /** What Hibernate's {@code ddl-auto} runs: the fields' columns in its key order, and no columns on the other side. */
    private void addForeignKeyAsHibernateDoes() {
        jdbc.execute("ALTER TABLE rule_definition_fields ADD CONSTRAINT fk_as_hibernate_writes_it "
                + "FOREIGN KEY (channel, rule_id, org_key) REFERENCES rule_definitions");
    }

    private List<String> primaryKeyColumns() {
        return jdbc.queryForList("SELECT k.column_name FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage k ON k.constraint_name = tc.constraint_name "
                        + "AND k.table_name = tc.table_name WHERE LOWER(tc.table_name) = 'rule_definitions' "
                        + "AND tc.constraint_type = 'PRIMARY KEY' ORDER BY k.ordinal_position", String.class)
                .stream().map(column -> column.toLowerCase(java.util.Locale.ROOT)).toList();
    }
}

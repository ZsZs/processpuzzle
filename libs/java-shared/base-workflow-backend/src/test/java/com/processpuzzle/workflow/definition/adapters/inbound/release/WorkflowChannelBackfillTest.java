package com.processpuzzle.workflow.definition.adapters.inbound.release;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** The six tables as they stood before channels, each keyed on its organization and id, moved onto channels. */
class WorkflowChannelBackfillTest {

    private JdbcTemplate jdbc;
    private WorkflowChannelBackfill backfill;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:workflow-backfill;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        WorkflowChannelBackfill.TABLES.forEach((table, key) -> {
            jdbc.execute("CREATE TABLE " + table + " (" + key.get(0) + " VARCHAR(63) NOT NULL, " + key.get(1)
                    + " VARCHAR(100) NOT NULL, CONSTRAINT " + table + "_pkey PRIMARY KEY (" + String.join(", ", key) + "))");
            jdbc.update("INSERT INTO " + table + " VALUES ('my-org', 'approval')");
        });
        backfill = new WorkflowChannelBackfill(jdbc, new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void everyExistingDefinitionBecomesItsDraftAndItsRelease_once() {
        backfill.migrate();

        WorkflowChannelBackfill.TABLES.keySet().forEach(table ->
                assertThat(jdbc.queryForList("SELECT channel FROM " + table + " ORDER BY channel", String.class))
                        .as(table).containsExactly("DRAFT", "RELEASED"));

        backfill.migrate();
        WorkflowChannelBackfill.TABLES.keySet().forEach(table ->
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).as(table).isEqualTo(2));
    }
}

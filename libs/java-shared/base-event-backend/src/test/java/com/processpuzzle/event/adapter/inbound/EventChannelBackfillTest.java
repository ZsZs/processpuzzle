package com.processpuzzle.event.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** The table as it stood before channels, keyed on {@code (org_key, id)}, moved onto channels. */
class EventChannelBackfillTest {

    private JdbcTemplate jdbc;
    private EventChannelBackfill backfill;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:event-backfill;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE event_definition (org_key VARCHAR(255) NOT NULL, id VARCHAR(255) NOT NULL, "
                + "name VARCHAR(255) NOT NULL, kind VARCHAR(255) NOT NULL, CONSTRAINT event_definition_pkey PRIMARY KEY (org_key, id))");
        jdbc.update("INSERT INTO event_definition (org_key, id, name, kind) VALUES ('my-org', 'OrderCreated', 'Order created', 'SYSTEM')");
        backfill = new EventChannelBackfill(jdbc, new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void everyExistingEventBecomesItsDraftAndItsRelease_once() {
        backfill.migrate();

        assertThat(jdbc.queryForList("SELECT channel FROM event_definition ORDER BY channel", String.class))
                .containsExactly("DRAFT", "RELEASED");

        backfill.migrate();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_definition", Integer.class)).isEqualTo(2);
    }
}

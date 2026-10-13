package com.processpuzzle.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** The registry's table as Hibernate creates it from Spring Modulith's mapping — the event columns 255 wide. */
class EventPublicationColumnsTest {

    private JdbcTemplate jdbc;
    private EventPublicationColumns columns;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:event-publication;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", ""));
        columns = new EventPublicationColumns(jdbc);
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void widensTheEventColumnsOnce_soThatALargeEventFits() {
        jdbc.execute("CREATE TABLE event_publication (id UUID PRIMARY KEY, event_type VARCHAR(255), "
                + "listener_id VARCHAR(255) NOT NULL, serialized_event VARCHAR(255) NOT NULL)");

        assertThat(columns.widenOnce()).containsExactly("serialized_event", "listener_id");

        String largeEvent = "x".repeat(10_000);
        jdbc.update("INSERT INTO event_publication (id, event_type, listener_id, serialized_event) VALUES (RANDOM_UUID(), 'e', ?, ?)",
                "l".repeat(1_000), largeEvent);
        assertThat(jdbc.queryForObject("SELECT LENGTH(serialized_event) FROM event_publication", Integer.class)).isEqualTo(10_000);
        assertThat(columns.widenOnce()).isEmpty();
    }

    @Test
    void leavesADatabaseWithoutTheRegistryAlone() {
        assertThatCode(() -> assertThat(columns.widenOnce()).isEmpty()).doesNotThrowAnyException();
    }
}

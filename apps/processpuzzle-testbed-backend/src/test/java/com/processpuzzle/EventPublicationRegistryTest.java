package com.processpuzzle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The event publication registry stores what the platform actually publishes. A {@code PlatformEvent}
 * carries an entity's whole payload; with the registry's default {@code varchar(255)} columns its INSERT
 * failed and took the publishing transaction — the Order being created — down with it. META-INF/orm.xml
 * widens the columns; H2 enforces the same limit, so this fails without it.
 */
@SpringBootTest
class EventPublicationRegistryTest {

    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void storesALargePlatformEventAndDeletesItOnceDelivered() {
        Map<String, Object> payload = Map.of("orderNumber", "E2E-1", "notes", "x".repeat(5_000));

        assertThatCode(() -> transactions.executeWithoutResult(status -> publisher.publishEvent(
                PlatformEvent.of("org-1", "order", "42", PlatformEventAction.CREATED, payload, Instant.now()))))
                .doesNotThrowAnyException();

        // completion-mode: delete — the catalog listener completed, so nothing is left outstanding.
        assertThat(jdbc.queryForObject("select count(*) from event_publication", Integer.class)).isZero();
    }
}

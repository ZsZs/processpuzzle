package com.processpuzzle.event.adapter.inbound;

import com.processpuzzle.core.definition.ChannelBackfill;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Moves {@code event_definition} onto channels once, so that every event an organization had before releases existed
 * is both its draft and its release; see {@link ChannelBackfill}. A single table with no children and no foreign key
 * onto it, so the generic backfill is the whole of it.
 *
 * <p>Ordered ahead of {@code DefaultEventDefinitionImporter}, whose inserts need the widened key. Never fails startup.
 */
@Component
public class EventChannelBackfill {

    static final String TABLE = "event_definition";
    private static final Logger LOG = LoggerFactory.getLogger(EventChannelBackfill.class);

    private final ChannelBackfill backfill;

    public EventChannelBackfill(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.backfill = new ChannelBackfill(jdbc, transactionManager);
    }

    @Order(5)
    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        try {
            backfill.migrate(TABLE, List.of("org_key", "id"));
        } catch (RuntimeException e) {
            LOG.warn("Failed to move {} onto channels; releasing an event definition will fail until it is.", TABLE, e);
        }
    }
}

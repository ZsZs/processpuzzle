package com.processpuzzle.state.adapter.inbound;

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
 * Moves {@code state_machine_definitions} and {@code diagram_definitions} onto channels once, so that every state
 * machine an organization had before releases existed — and the layout drawn for it — is both its draft and its
 * release; see {@link ChannelBackfill}.
 *
 * <p>Ordered ahead of {@code DefaultStateImporter}, whose inserts need the widened primary keys. Like the importer it
 * never fails startup: a problem is logged, and the next start tries again.
 */
@Component
public class StateChannelBackfill {

    private static final Logger LOG = LoggerFactory.getLogger(StateChannelBackfill.class);
    private static final List<String> KEY = List.of("org_key", "entity_name");

    private final ChannelBackfill backfill;

    public StateChannelBackfill(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.backfill = new ChannelBackfill(jdbc, transactionManager);
    }

    @Order(5)
    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        for (String table : List.of("state_machine_definitions", "diagram_definitions")) {
            try {
                backfill.migrate(table, KEY);
            } catch (RuntimeException e) {
                LOG.warn("Failed to move {} onto channels; releasing a state machine will fail until it is.", table, e);
            }
        }
    }
}

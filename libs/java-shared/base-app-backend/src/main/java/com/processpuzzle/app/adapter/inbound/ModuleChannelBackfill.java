package com.processpuzzle.app.adapter.inbound;

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
 * Moves {@code app_modules} onto channels once, so that every module an organization had before
 * releases existed is both its draft and its release; see {@link ChannelBackfill}.
 *
 * <p>Ordered ahead of {@code DefaultAppLoader}, whose inserts need the widened primary key. Like the
 * loader it never fails startup: a problem is logged, and the next start tries again.
 */
@Component
public class ModuleChannelBackfill {

    private static final Logger LOG = LoggerFactory.getLogger(ModuleChannelBackfill.class);

    private final ChannelBackfill backfill;

    public ModuleChannelBackfill(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.backfill = new ChannelBackfill(jdbc, transactionManager);
    }

    @Order(5)
    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        try {
            backfill.migrate("app_modules", List.of("org_key", "module_key"));
        } catch (RuntimeException e) {
            LOG.warn("Failed to move app_modules onto channels; releasing a module will fail until it is.", e);
        }
    }
}

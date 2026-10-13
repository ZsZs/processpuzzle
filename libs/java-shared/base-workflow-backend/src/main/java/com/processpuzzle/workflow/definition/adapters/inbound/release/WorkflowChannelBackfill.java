package com.processpuzzle.workflow.definition.adapters.inbound.release;

import com.processpuzzle.core.definition.ChannelBackfill;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Moves the workflow definition tables onto channels once, so that every workflow and catalogue entry an organization
 * had before releases existed is both its draft and its release; see {@link ChannelBackfill}. Each is a single row per
 * definition, with no children and no foreign key onto it, so the generic backfill is the whole of it.
 *
 * <p>Ordered ahead of {@code DefaultWorkflowImporter}, whose inserts need the widened keys. Never fails startup.
 */
@Component
public class WorkflowChannelBackfill {

    /** Each table and the key it had before channels. */
    static final Map<String, List<String>> TABLES = Map.of(
            "workflow_definition", List.of("org_key", "id"),
            "workflow_role_definition", List.of("org_key", "id"),
            "workflow_task_definition", List.of("org_key", "id"),
            "workflow_artifact_definition", List.of("org_key", "id"),
            "workflow_tool_definition", List.of("org_key", "id"),
            "workflow_diagram", List.of("org_key", "workflow_id"));
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowChannelBackfill.class);

    private final ChannelBackfill backfill;

    public WorkflowChannelBackfill(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.backfill = new ChannelBackfill(jdbc, transactionManager);
    }

    @Order(5)
    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        TABLES.forEach((table, key) -> {
            try {
                backfill.migrate(table, key);
            } catch (RuntimeException e) {
                LOG.warn("Failed to move {} onto channels; releasing a workflow will fail until it is.", table, e);
            }
        });
    }
}

package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.usecases.inbound.SyncRoleDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs one reconcile pass over every organization's role catalog at startup, so drift heals itself.
 *
 * <p>Drift is expected rather than exceptional: the projection is best-effort after commit (see
 * {@code RoleDirectorySyncListener}), a realm can be re-imported or a role deleted by hand, and a
 * deployment may have run for a while with no admin secret configured at all. This pass is what makes
 * those recoverable without anyone re-saving definitions one by one.
 *
 * <p>Ordered after {@link DefaultWorkflowImporter} so that a fresh deployment's seeded roles are
 * already in the catalog when the diff is taken. The two orders are explicit constants because
 * Spring's default for an unannotated {@code @EventListener} is {@code LOWEST_PRECEDENCE}, which
 * would leave the relative order of the two arbitrary.
 *
 * <p>Nothing here can fail startup: {@link SyncRoleDirectory#reconcile()} is best-effort per
 * organization, and anything it cannot do it logs.
 */
@Component
@ConditionalOnProperty(name = "processpuzzle.workflow.role-sync.reconcile-on-startup",
        matchIfMissing = true)
@Order(RoleDirectoryReconciler.RECONCILE_ORDER)
public class RoleDirectoryReconciler {

    /** After {@link DefaultWorkflowImporter#SEED_ORDER}: reconcile the catalog the importer left. */
    public static final int RECONCILE_ORDER = DefaultWorkflowImporter.SEED_ORDER + 10;

    private static final Logger LOG = LoggerFactory.getLogger(RoleDirectoryReconciler.class);

    private final SyncRoleDirectory syncRoleDirectory;

    public RoleDirectoryReconciler(SyncRoleDirectory syncRoleDirectory) {
        this.syncRoleDirectory = syncRoleDirectory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        LOG.info("Reconciling workflow role definitions with the identity provider's realm roles.");
        syncRoleDirectory.reconcile();
    }
}

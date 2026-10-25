package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.usecases.inbound.SyncRoleDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <p>The identity provider may not be ready when the backend starts: the realm and its admin client are
 * created by a separate deployment resource, and the two race. A pass that found Keycloak unreachable is
 * therefore repeated every {@code retry-interval} (default 30s) until one completes, up to
 * {@link #MAX_RETRIES} times, so no restart is needed once Keycloak is up.
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

    /** Retries after the startup pass; with the default 30s interval that is about ten minutes. */
    static final int MAX_RETRIES = 20;

    private final SyncRoleDirectory syncRoleDirectory;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final AtomicInteger retries = new AtomicInteger();

    public RoleDirectoryReconciler(SyncRoleDirectory syncRoleDirectory) {
        this.syncRoleDirectory = syncRoleDirectory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        LOG.info("Reconciling workflow role definitions with the identity provider's realm roles.");
        pending.set(!syncRoleDirectory.reconcile());
    }

    @Scheduled(fixedDelayString = "${processpuzzle.workflow.role-sync.retry-interval:PT30S}",
            initialDelayString = "${processpuzzle.workflow.role-sync.retry-interval:PT30S}")
    public void retryWhilePending() {
        if (!pending.get()) {
            return;
        }
        if (retries.incrementAndGet() > MAX_RETRIES) {
            pending.set(false);
            LOG.warn("Giving up reconciling workflow role definitions after {} retries; the next restart "
                    + "or release of a role definition will retry.", MAX_RETRIES);
            return;
        }
        LOG.info("Retrying the workflow role reconcile ({}/{}).", retries.get(), MAX_RETRIES);
        pending.set(!syncRoleDirectory.reconcile());
    }
}

package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.domain.event.RoleDefinitionChangedEvent;
import com.processpuzzle.workflow.definition.domain.event.RoleDefinitionDeletedEvent;
import com.processpuzzle.workflow.definition.usecases.inbound.SyncRoleDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RoleDirectorySyncListenerTest {

    private final SyncRoleDirectory sync = mock(SyncRoleDirectory.class);
    private final RoleDirectorySyncListener listener = new RoleDirectorySyncListener(sync);

    @Test
    void delegatesBothEventsToTheSyncUseCase() {
        listener.onRoleDefinitionChanged(
                new RoleDefinitionChangedEvent("acme", "reviewer", "Reviewer", "Reviews submissions"));
        verify(sync).sync("acme", "reviewer", "Reviewer", "Reviews submissions");

        listener.onRoleDefinitionDeleted(new RoleDefinitionDeletedEvent("acme", "reviewer"));
        verify(sync).remove("acme", "reviewer");
    }

    /**
     * The phase is the design decision, not a detail. Before commit — the phase base-app's
     * {@code TenantDataCleaner} needs, because it cascades <em>rows</em> — this handler could publish
     * a role into a realm and then have the definition rolled out from under it, leaving a realm role
     * no tenant authored and nothing to ever remove it. Asserted by reflection because there is no
     * other way for a unit test to notice the annotation changing.
     */
    @Test
    void observesAfterCommitSoAProjectionNeverOutlivesARolledBackWrite() throws NoSuchMethodException {
        for (Method method : new Method[]{
                RoleDirectorySyncListener.class.getMethod(
                        "onRoleDefinitionChanged", RoleDefinitionChangedEvent.class),
                RoleDirectorySyncListener.class.getMethod(
                        "onRoleDefinitionDeleted", RoleDefinitionDeletedEvent.class)}) {
            TransactionalEventListener annotation =
                    method.getAnnotation(TransactionalEventListener.class);
            assertThat(annotation).as("%s must be transactional", method.getName()).isNotNull();
            assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        }
    }

    /** Ordering the reconcile after the seed importer, so a fresh deployment's roles are in the diff. */
    @Test
    void reconcilerRunsAfterTheDefaultWorkflowImporter() {
        assertThat(RoleDirectoryReconciler.RECONCILE_ORDER)
                .isGreaterThan(DefaultWorkflowImporter.SEED_ORDER);

        new RoleDirectoryReconciler(sync).reconcileOnStartup();
        verify(sync).reconcile();
    }
}

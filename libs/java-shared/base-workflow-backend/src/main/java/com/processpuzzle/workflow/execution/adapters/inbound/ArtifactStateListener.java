package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps a bound artifact's {@code currentState} what its record's state is: every {@code STATE_CHANGED} fact about a
 * record — base-state's for an entity, base-document's for a document — refreshes the artifact instances bound to it in
 * the runs still going (docs/event-parameters.md). A finished run keeps the state its records were in when it ended.
 *
 * <p>Listens to the raw {@link PlatformEvent}, not to catalogued occurrences: the state matters whether or not an event
 * definition names that change. {@code @TransactionalEventListener} with {@code REQUIRES_NEW}, as {@link PlatformEvent}
 * asks; idempotent, since a redelivered fact writes the same state again.
 */
@Component
public class ArtifactStateListener {

    private static final Set<WorkflowInstanceStatus> RUNNING = EnumSet.of(WorkflowInstanceStatus.ACTIVE, WorkflowInstanceStatus.SUSPENDED);

    private final ArtifactInstanceRepository artifactInstances;
    private final WorkflowInstanceRepository workflowInstances;

    public ArtifactStateListener(ArtifactInstanceRepository artifactInstances, WorkflowInstanceRepository workflowInstances) {
        this.artifactInstances = artifactInstances;
        this.workflowInstances = workflowInstances;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PlatformEvent fact) {
        if (fact.action() != PlatformEventAction.STATE_CHANGED || fact.subjectId() == null) {
            return;
        }
        for (ArtifactInstance artifact : artifactInstances.findByOrgKeyAndEntityTypeAndEntityId(fact.orgKey(), fact.subjectType(), fact.subjectId())) {
            if (!Objects.equals(artifact.getCurrentState(), fact.state()) && isRunning(fact.orgKey(), artifact)) {
                artifact.setCurrentState(fact.state());
                artifact.setUpdatedAt(Instant.now());
                artifactInstances.save(artifact);
            }
        }
    }

    private boolean isRunning(String orgKey, ArtifactInstance artifact) {
        return workflowInstances.findByOrgKeyAndId(orgKey, artifact.getWorkflowInstanceId())
                .filter(instance -> RUNNING.contains(instance.getStatus()))
                .isPresent();
    }
}

package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.RequiredStartArtifact;
import com.processpuzzle.workflow.definition.domain.StartEvent;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.OccurredEventDocument;
import com.processpuzzle.workflow.execution.domain.PayloadPath;
import com.processpuzzle.workflow.execution.usecases.inbound.StartEventAdmission;
import com.processpuzzle.workflow.execution.usecases.inbound.StartWorkflowInstanceUseCase;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts the workflows whose INPUT_ARTIFACT start event a platform fact completes. A required ENTITY
 * artifact matches the fact when its entity type is the fact's {@code subjectType} and either it names no
 * state and the object was CREATED, or the object entered exactly the state it names. The remaining
 * required artifacts are then checked as an explicit start checks them
 * ({@link StartEventAdmission#requiredArtifactsInState}) — the matched ones are not read back, the fact
 * itself proves them; when they hold, the subject becomes the
 * instance's {@code entityId} and the start event's {@code payloadMapping}, evaluated against the fact
 * ({@code $.subjectId}, {@code $.state}, {@code $.payload.<attribute>}), its initial context.
 *
 * <p>Listens to the raw {@link PlatformEvent} rather than to base-event's catalogued occurrences: the
 * required state names the condition already, and asking for it to be catalogued too would be a second
 * place to say the same thing. base-state publishes a STATE_CHANGED fact for an object's initial state as
 * well, so a start waiting for DRAFT fires on creation.
 *
 * <p>{@code @TransactionalEventListener} with {@code REQUIRES_NEW}, as {@link PlatformEvent} asks; each
 * start runs in a transaction of its own through {@code startTriggered}, which is idempotent per subject,
 * so one workflow that cannot start is logged and a redelivered fact starts nothing twice.
 */
@Component
public class InputArtifactStartListener {

    private static final Logger LOG = LoggerFactory.getLogger(InputArtifactStartListener.class);

    private final WorkflowRepository workflowRepository;
    private final ResolveWorkflowUseCase resolveWorkflow;
    private final StartEventAdmission startEventAdmission;
    private final StartWorkflowInstanceUseCase startWorkflowInstance;

    public InputArtifactStartListener(WorkflowRepository workflowRepository, ResolveWorkflowUseCase resolveWorkflow,
                                      StartEventAdmission startEventAdmission,
                                      StartWorkflowInstanceUseCase startWorkflowInstance) {
        this.workflowRepository = workflowRepository;
        this.resolveWorkflow = resolveWorkflow;
        this.startEventAdmission = startEventAdmission;
        this.startWorkflowInstance = startWorkflowInstance;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PlatformEvent event) {
        if (event.subjectId() == null || event.subjectType() == null
                || (event.action() != PlatformEventAction.CREATED && event.action() != PlatformEventAction.STATE_CHANGED)) {
            return;
        }
        for (Workflow workflow : workflowRepository.findByOrgKey(event.orgKey())) {
            List<StartEvent> candidates = inputArtifactStartEvents(workflow);
            if (!candidates.isEmpty()) {
                startIfMatched(event, workflow, candidates);
            }
        }
    }

    private void startIfMatched(PlatformEvent event, Workflow workflow, List<StartEvent> candidates) {
        try {
            ResolvedWorkflow resolved = resolveWorkflow.resolveByOrgKeyAndId(event.orgKey(), workflow.getId());
            candidates.stream()
                    .filter(startEvent -> !matched(event, resolved, startEvent).isEmpty())
                    .filter(startEvent -> startEventAdmission.requiredArtifactsInState(event.orgKey(), resolved,
                            startEvent, event.subjectId(), matched(event, resolved, startEvent)))
                    .findFirst()
                    .ifPresent(startEvent -> start(event, workflow, startEvent));
        } catch (RuntimeException e) {
            LOG.error("{} {} of {}/{} should have been checked against workflow '{}', but could not.", event.orgKey(),
                    event.action(), event.subjectType(), event.subjectId(), workflow.getId(), e);
        }
    }

    private void start(PlatformEvent event, Workflow workflow, StartEvent startEvent) {
        Map<String, Object> context = PayloadPath.map(OccurredEventDocument.of(event), startEvent.getPayloadMapping());
        startWorkflowInstance.startTriggered(event.orgKey(), workflow.getId(), startEvent.getId(), event.subjectId(),
                        event.subjectType(), context)
                .ifPresentOrElse(
                        instance -> LOG.info("{}/{} ({}) started workflow '{}' as instance {}.", event.subjectType(),
                                event.subjectId(), event.state() == null ? event.action() : event.state(),
                                workflow.getId(), instance.getId()),
                        () -> LOG.debug("{}/{}: workflow '{}' is already running for it.", event.subjectType(),
                                event.subjectId(), workflow.getId()));
    }

    /** The ids of the required artifacts {@code event} completes; empty when it completes none. */
    static List<String> matched(PlatformEvent event, ResolvedWorkflow workflow, StartEvent startEvent) {
        return orEmpty(startEvent.getRequiredArtifacts()).stream()
                .filter(required -> event.subjectType().equals(entityTypeOf(workflow, required))
                        && (required.getState() == null
                                ? event.action() == PlatformEventAction.CREATED
                                : event.action() == PlatformEventAction.STATE_CHANGED
                                        && required.getState().equals(event.state())))
                .map(RequiredStartArtifact::getArtifactDefinitionId)
                .toList();
    }

    private static String entityTypeOf(ResolvedWorkflow workflow, RequiredStartArtifact required) {
        return workflow.artifacts().stream()
                .filter(artifact -> artifact.getId().equals(required.getArtifactDefinitionId()))
                .filter(artifact -> artifact.getArtifactType() == ArtifactType.ENTITY)
                .map(ArtifactDefinition::getArtifactTypeId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static List<StartEvent> inputArtifactStartEvents(Workflow workflow) {
        return orEmpty(workflow.getStartEvents()).stream()
                .filter(startEvent -> startEvent.getStartType() == WorkflowStartConditionType.INPUT_ARTIFACT)
                .filter(startEvent -> !orEmpty(startEvent.getRequiredArtifacts()).isEmpty())
                .toList();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}

package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.shared.event.DefinedEventOccurred;
import com.processpuzzle.workflow.definition.domain.StartEvent;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.execution.domain.OccurredEventDocument;
import com.processpuzzle.workflow.execution.domain.PayloadPath;
import com.processpuzzle.workflow.execution.usecases.inbound.StartWorkflowInstanceUseCase;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts the workflows a catalogued event triggers: every workflow of the organization with a
 * TRIGGERING_EVENT start event whose {@code eventType} is the occurred event's definition id. The
 * subject of the event becomes the instance's {@code entityId}, and the start event's
 * {@code payloadMapping} — {@link PayloadPath}s into the {@link DefinedEventOccurred}, so
 * {@code $.subjectId} or {@code $.payload.customer} — becomes its initial context.
 *
 * <p>The start events are filtered in memory rather than by a JSONB query: an organization has tens of
 * workflows, not thousands, and the library's tests run on H2, which has no JSONB operators.
 *
 * <p>{@code @TransactionalEventListener} with {@code REQUIRES_NEW}, as {@code DefinedEventOccurred}
 * asks. Each start runs in a transaction of its own ({@code startTriggered} is {@code REQUIRES_NEW}),
 * so one workflow that cannot start is logged and the others still start. Redelivery is harmless:
 * {@code startTriggered} does nothing for a subject that already has a running instance.
 */
@Component
public class TriggeredStartListener {

    private static final Logger LOG = LoggerFactory.getLogger(TriggeredStartListener.class);

    private final WorkflowRepository workflowRepository;
    private final StartWorkflowInstanceUseCase startWorkflowInstance;

    public TriggeredStartListener(WorkflowRepository workflowRepository,
                                  StartWorkflowInstanceUseCase startWorkflowInstance) {
        this.workflowRepository = workflowRepository;
        this.startWorkflowInstance = startWorkflowInstance;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(DefinedEventOccurred event) {
        for (Workflow workflow : workflowRepository.findByOrgKey(event.orgKey())) {
            List<StartEvent> startEvents = workflow.getStartEvents() == null ? List.of() : workflow.getStartEvents();
            startEvents.stream()
                    .filter(startEvent -> startEvent.getStartType() == WorkflowStartConditionType.TRIGGERING_EVENT)
                    .filter(startEvent -> event.eventDefinitionId().equals(startEvent.getEventType()))
                    .findFirst()
                    .ifPresent(startEvent -> start(event, workflow, startEvent));
        }
    }

    private void start(DefinedEventOccurred event, Workflow workflow, StartEvent startEvent) {
        try {
            Map<String, Object> context = PayloadPath.map(OccurredEventDocument.of(event), startEvent.getPayloadMapping());
            startWorkflowInstance.startTriggered(
                            event.orgKey(), workflow.getId(), startEvent.getId(), event.subjectId(),
                            event.subjectType(), context)
                    .ifPresentOrElse(
                            instance -> LOG.info("'{}' on {}/{} started workflow '{}' as instance {}.",
                                    event.eventDefinitionId(), event.subjectType(), event.subjectId(),
                                    workflow.getId(), instance.getId()),
                            () -> LOG.debug("'{}' on {}/{}: workflow '{}' is already running for it.",
                                    event.eventDefinitionId(), event.subjectType(), event.subjectId(), workflow.getId()));
        } catch (RuntimeException e) {
            LOG.error("'{}' on {}/{} should have started workflow '{}' by start event '{}', but could not.",
                    event.eventDefinitionId(), event.subjectType(), event.subjectId(), workflow.getId(),
                    startEvent.getId(), e);
        }
    }
}

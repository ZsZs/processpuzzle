package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.shared.event.DefinedEventOccurred;
import com.processpuzzle.workflow.definition.domain.EventUse;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.OccurredEventDocument;
import com.processpuzzle.workflow.execution.domain.PayloadPath;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers an occurred event to one waiting catch: records what the catch's payload mapping takes
 * from the event as its context contribution, and hands over to {@link CatchOccurrence}, which marks
 * it OCCURRED, interrupts the task of an interrupting boundary event, and moves the instance on — the
 * tasks waiting for the catch become eligible, and the instance is closed if that was the last thing
 * it waited for.
 *
 * <p>Does nothing unless the catch is still WAITING and its instance still ACTIVE, which is what
 * makes a redelivered occurrence harmless: the first delivery moved the catch on.
 *
 * <p>{@code REQUIRES_NEW}: the caller is an after-commit listener delivering to several catches, and
 * one that fails — typically on the catch's optimistic lock, because another delivery got there first
 * — must neither roll back nor poison the others.
 */
@Component
public class OccurCatchEventUseCase {

    private final EventInstanceRepository eventInstanceRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final ResolveWorkflowUseCase resolveWorkflow;
    private final CatchOccurrence catchOccurrence;

    public OccurCatchEventUseCase(EventInstanceRepository eventInstanceRepository,
                                  WorkflowInstanceRepository workflowInstanceRepository,
                                  ResolveWorkflowUseCase resolveWorkflow,
                                  CatchOccurrence catchOccurrence) {
        this.eventInstanceRepository = eventInstanceRepository;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.resolveWorkflow = resolveWorkflow;
        this.catchOccurrence = catchOccurrence;
    }

    /** @return whether the event was delivered; false when the catch no longer waits */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean occur(String orgKey, UUID eventInstanceId, DefinedEventOccurred occurred) {
        EventInstance event = eventInstanceRepository.findByOrgKeyAndId(orgKey, eventInstanceId).orElse(null);
        if (event == null || !event.isCatch() || event.getStatus() != EventInstanceStatus.WAITING) {
            return false;
        }
        WorkflowInstance instance =
                workflowInstanceRepository.findByOrgKeyAndId(orgKey, event.getWorkflowInstanceId()).orElse(null);
        if (instance == null || instance.getStatus() != WorkflowInstanceStatus.ACTIVE) {
            return false;
        }

        ResolvedWorkflow definition = resolveWorkflow.resolveByOrgKeyAndId(orgKey, instance.getWorkflowId());
        Map<String, String> mapping = definition.definition().findEventUse(event.getEventUseId())
                .map(EventUse::getPayloadMapping)
                .orElse(null);
        catchOccurrence.occur(orgKey, definition, instance, event,
                new CatchOccurrence.Occurrence(Instant.now(), occurred.occurrenceId(),
                        occurred.payload(), PayloadPath.map(OccurredEventDocument.of(occurred), mapping)));
        return true;
    }
}

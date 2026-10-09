package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.shared.event.DefinedEventOccurred;
import com.processpuzzle.workflow.definition.domain.EventDirection;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.usecases.inbound.OccurCatchEventUseCase;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers a catalogued event to the intermediate catch events waiting for it, by its kind:
 *
 * <ul>
 *   <li>SYSTEM — to every waiting catch of the event whose correlation value — the instance's
 *       subject — is the event's {@code subjectId}, and whose instance's entity type, when both sides
 *       name one, is the event's {@code subjectType}.</li>
 *   <li>MESSAGE — to exactly one catch: the longest-waiting one whose correlation value equals the
 *       message's, other than in the instance that threw it. A message nobody waits for is dropped —
 *       there is no buffer — and one already delivered (a redelivery: some catch already recorded its
 *       occurrence id) is skipped.</li>
 *   <li>SIGNAL — to every waiting catch of the event.</li>
 * </ul>
 *
 * <p>An event without a kind was published before kinds existed, and is SYSTEM.
 *
 * <p>Each delivery is its own transaction ({@link OccurCatchEventUseCase}). For SYSTEM and SIGNAL
 * every catch is tried, and the first failure is rethrown afterwards, so that the event publication
 * registry redelivers the event; that is safe, because a catch delivered to already is no longer
 * WAITING. For a MESSAGE, losing the optimistic-lock race on one catch moves on to the next.
 *
 * <p>{@code @TransactionalEventListener} with {@code REQUIRES_NEW}, as {@code DefinedEventOccurred}
 * prescribes.
 */
@Component
public class IntermediateCatchListener {

    private static final Logger LOG = LoggerFactory.getLogger(IntermediateCatchListener.class);

    private final EventInstanceRepository eventInstanceRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final OccurCatchEventUseCase occurCatchEvent;

    public IntermediateCatchListener(EventInstanceRepository eventInstanceRepository,
                                     WorkflowInstanceRepository workflowInstanceRepository,
                                     OccurCatchEventUseCase occurCatchEvent) {
        this.eventInstanceRepository = eventInstanceRepository;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.occurCatchEvent = occurCatchEvent;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(DefinedEventOccurred event) {
        switch (event.effectiveKind()) {
            case SYSTEM -> deliverToAll(event, systemCatches(event));
            case MESSAGE -> deliverMessage(event);
            case SIGNAL -> deliverToAll(event, eventInstanceRepository
                    .findByOrgKeyAndEventDefinitionIdAndDirectionAndStatusOrderByWaitingSinceAscIdAsc(
                            event.orgKey(), event.eventDefinitionId(), EventDirection.CATCH, EventInstanceStatus.WAITING));
        }
    }

    private List<EventInstance> systemCatches(DefinedEventOccurred event) {
        if (event.subjectId() == null) {
            return List.of();
        }
        return waitingFor(event, event.subjectId()).stream()
                .filter(catchEvent -> subjectTypeMatches(event, catchEvent))
                .toList();
    }

    private boolean subjectTypeMatches(DefinedEventOccurred event, EventInstance catchEvent) {
        if (event.subjectType() == null) {
            return true;
        }
        return workflowInstanceRepository.findByOrgKeyAndId(event.orgKey(), catchEvent.getWorkflowInstanceId())
                .map(instance -> instance.getEntityType() == null || instance.getEntityType().equals(event.subjectType()))
                .orElse(false);
    }

    private void deliverMessage(DefinedEventOccurred event) {
        if (event.occurrenceId() != null && eventInstanceRepository.existsByOrgKeyAndOccurrenceIdAndDirection(
                event.orgKey(), event.occurrenceId(), EventDirection.CATCH)) {
            LOG.debug("{}: message '{}' {} was delivered already.", event.orgKey(), event.eventDefinitionId(),
                    event.occurrenceId());
            return;
        }
        List<EventInstance> candidates = event.correlationValue() == null ? List.of() : waitingFor(event, event.correlationValue())
                .stream()
                .filter(catchEvent -> !Objects.equals(catchEvent.getWorkflowInstanceId(), event.sourceWorkflowInstanceId()))
                .toList();
        for (EventInstance candidate : candidates) {
            try {
                if (occurCatchEvent.occur(event.orgKey(), candidate.getId(), event)) {
                    LOG.info("{}: message '{}' ({}) delivered to workflow instance {}.", event.orgKey(),
                            event.eventDefinitionId(), event.correlationValue(), candidate.getWorkflowInstanceId());
                    return;
                }
            } catch (OptimisticLockingFailureException raced) {
                LOG.debug("{}: catch {} was taken concurrently; trying the next.", event.orgKey(), candidate.getId());
            }
        }
        LOG.info("{}: message '{}' ({}) has no waiting recipient; dropped.", event.orgKey(), event.eventDefinitionId(),
                event.correlationValue());
    }

    private void deliverToAll(DefinedEventOccurred event, List<EventInstance> catches) {
        RuntimeException firstFailure = null;
        for (EventInstance catchEvent : catches) {
            try {
                occurCatchEvent.occur(event.orgKey(), catchEvent.getId(), event);
            } catch (RuntimeException e) {
                LOG.warn("{}: delivering '{}' to catch {} failed; the event will be redelivered.", event.orgKey(),
                        event.eventDefinitionId(), catchEvent.getId(), e);
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private List<EventInstance> waitingFor(DefinedEventOccurred event, String correlationValue) {
        return eventInstanceRepository
                .findByOrgKeyAndEventDefinitionIdAndDirectionAndStatusAndCorrelationValueOrderByWaitingSinceAscIdAsc(
                        event.orgKey(), event.eventDefinitionId(), EventDirection.CATCH, EventInstanceStatus.WAITING,
                        correlationValue);
    }
}

package com.processpuzzle.event.adapter.inbound;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.event.usecase.FindEventDefinition;
import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.shared.event.DefinedEventOccurred;
import com.processpuzzle.shared.event.EventThrown;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Recognises events a workflow threw: an {@link EventThrown} naming a MESSAGE or SIGNAL definition of
 * the organization's catalog is republished as a {@link DefinedEventOccurred} of that kind, carrying
 * the thrower's correlation value and occurrence id. That keeps this module the only publisher of
 * occurrences — a catching workflow observes the same event whatever raised it.
 *
 * <p>A throw naming no definition, or a SYSTEM one, is logged and dropped rather than refused: the
 * workflow validator checks both when a workflow is saved, so getting here means the catalog changed
 * since, and an exception would only make the publication registry redeliver it forever.
 *
 * <p>{@code @TransactionalEventListener} with {@code REQUIRES_NEW}, for the reasons
 * {@link PlatformEventCatalogListener} gives.
 */
@Component
class ThrownEventCatalogListener {

    private static final Logger LOG = LoggerFactory.getLogger(ThrownEventCatalogListener.class);

    private final FindEventDefinition findEventDefinition;
    private final ApplicationEventPublisher eventPublisher;

    ThrownEventCatalogListener(FindEventDefinition findEventDefinition, ApplicationEventPublisher eventPublisher) {
        this.findEventDefinition = findEventDefinition;
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(EventThrown thrown) {
        Optional<EventDefinition> definition = findEventDefinition.lookup(thrown.orgKey(), thrown.eventDefinitionId());
        if (definition.isEmpty()) {
            LOG.warn("{}: workflow instance {} threw '{}', which the event catalog does not define; dropped.",
                    thrown.orgKey(), thrown.sourceWorkflowInstanceId(), thrown.eventDefinitionId());
            return;
        }
        EventKind kind = definition.get().getKind();
        if (kind == EventKind.SYSTEM) {
            LOG.warn("{}: workflow instance {} threw SYSTEM event '{}', which only the platform raises; dropped.",
                    thrown.orgKey(), thrown.sourceWorkflowInstanceId(), thrown.eventDefinitionId());
            return;
        }
        LOG.debug("{}: workflow instance {} threw {} '{}'.", thrown.orgKey(), thrown.sourceWorkflowInstanceId(), kind,
                thrown.eventDefinitionId());
        eventPublisher.publishEvent(new DefinedEventOccurred(thrown.orgKey(), thrown.eventDefinitionId(),
                thrown.subjectType(), thrown.subjectId(), thrown.payload(), thrown.occurredAt(),
                CatalogEventKind.valueOf(kind.name()), thrown.occurrenceId(), thrown.correlationValue(),
                thrown.sourceWorkflowInstanceId()));
    }
}

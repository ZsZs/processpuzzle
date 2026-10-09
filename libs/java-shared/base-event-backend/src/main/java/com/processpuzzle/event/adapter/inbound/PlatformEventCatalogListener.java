package com.processpuzzle.event.adapter.inbound;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.shared.event.DefinedEventOccurred;
import com.processpuzzle.shared.event.PlatformEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Recognises raw platform facts: every SYSTEM {@link EventDefinition} of the fact's organization whose
 * binding matches it is republished as a {@link DefinedEventOccurred}. That is all — nothing is
 * stored, and a fact no definition names is dropped.
 *
 * <p>{@code @TransactionalEventListener} with {@code REQUIRES_NEW}, the same shape as base-state's
 * {@code EntityObjectCreatedListener}: after commit, because a fact that rolled back never happened;
 * and in a transaction of its own, because the {@code DefinedEventOccurred} published here is itself
 * transactional — its subscribers fire when <em>this</em> transaction commits, and with the
 * publishing transaction already finished they would otherwise never fire at all.
 *
 * <p>An exception propagates on purpose: the event publication registry then keeps the publication
 * incomplete and redelivers it, which is the point of having one.
 */
@Component
class PlatformEventCatalogListener {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformEventCatalogListener.class);

    private final EventDefinitionRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    PlatformEventCatalogListener(EventDefinitionRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PlatformEvent fact) {
        List<EventDefinition> candidates = repository.findByOrgKeyAndKindAndSubjectTypeAndAction(
                fact.orgKey(), EventKind.SYSTEM, fact.subjectType(), fact.action());
        for (EventDefinition definition : candidates) {
            if (definition.matches(fact)) {
                LOG.debug("{} {} {}/{} is '{}'.", fact.orgKey(), fact.action(), fact.subjectType(), fact.subjectId(),
                        definition.getId());
                eventPublisher.publishEvent(new DefinedEventOccurred(fact.orgKey(), definition.getId(),
                        fact.subjectType(), fact.subjectId(), fact.payload(), fact.occurredAt()));
            }
        }
    }
}

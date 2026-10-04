package com.processpuzzle.composition;

import com.processpuzzle.ai.galleries.SubjectGalleries;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectCreatedEvent;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectDeletedEvent;
import com.processpuzzle.baseentity.instances.domain.event.EntityObjectUpdatedEvent;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays base-entity's object lifecycle onto the AI feature, so that a subject's gallery follows the
 * photos attached to it: a boat saved with a new photo is enrolled without anyone asking. base-ai
 * subscribes to nothing itself — it would have to name base-entity's events — which is why the relay
 * lives here, with the other introductions between features.
 *
 * <p>After commit, and fire-and-forget: the change is a fact by the time this runs, and a gallery that
 * cannot be synchronized now is synchronized again before the subject is next recognized. base-ai
 * opens its own transactions and submits to the vision server off this thread, so the saving request
 * is not held up by it.
 */
@Component
class AiSubjectChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(AiSubjectChangeListener.class);

    private final SubjectGalleries galleries;

    AiSubjectChangeListener(SubjectGalleries galleries) {
        this.galleries = galleries;
    }

    @TransactionalEventListener
    public void on(EntityObjectCreatedEvent event) {
        changed(event.orgKey(), event.entityDefinitionCode(), event.objectId());
    }

    @TransactionalEventListener
    public void on(EntityObjectUpdatedEvent event) {
        changed(event.orgKey(), event.entityDefinitionCode(), event.objectId());
    }

    @TransactionalEventListener
    public void on(EntityObjectDeletedEvent event) {
        try {
            galleries.subjectDeleted(event.orgKey(), event.entityDefinitionCode(), event.objectId());
        } catch (RuntimeException e) {
            LOG.warn("gallery of deleted {}/{} not discarded: {}", event.entityDefinitionCode(), event.objectId(), e.getMessage());
        }
    }

    private void changed(String orgKey, String entityName, UUID objectId) {
        try {
            galleries.subjectChanged(orgKey, entityName, objectId);
        } catch (RuntimeException e) {
            LOG.warn("gallery of {}/{} not synchronized: {}", entityName, objectId, e.getMessage());
        }
    }
}

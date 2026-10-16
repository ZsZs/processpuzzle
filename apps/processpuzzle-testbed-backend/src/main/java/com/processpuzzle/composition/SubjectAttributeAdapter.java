package com.processpuzzle.composition;

import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.event.usecase.SubjectAttributePort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Answers base-event's question whether an entity type has an attribute — what a SYSTEM event's declared parameter
 * is filled from — from base-entity's drafts, the definitions a designer saving an event works against.
 */
@Component
public class SubjectAttributeAdapter implements SubjectAttributePort {

    private final EntityAttributeQuery attributeQuery;

    public SubjectAttributeAdapter(EntityAttributeQuery attributeQuery) {
        this.attributeQuery = attributeQuery;
    }

    @Override
    public Optional<Boolean> hasAttribute(String orgKey, String subjectType, String attributeName) {
        if (!attributeQuery.entityTypeExists(orgKey, subjectType, Channel.DRAFT)) {
            return Optional.empty();
        }
        return Optional.of(attributeQuery.attributeKind(orgKey, subjectType, attributeName, Channel.DRAFT).isPresent());
    }
}

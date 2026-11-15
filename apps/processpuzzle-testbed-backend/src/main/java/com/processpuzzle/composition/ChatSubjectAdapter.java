package com.processpuzzle.composition;

import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.chat.conversation.usecases.outbound.SubjectInfo;
import com.processpuzzle.chat.conversation.usecases.outbound.SubjectResolverPort;
import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.state.api.StateOperationApi;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Tells base-chat which subjects exist and in which state, from base-entity and base-state. Neither library
 * names the other; see the package documentation.
 *
 * <p>An entity instance has no owner here: base-entity's {@code EntityObjectView} does not carry who created
 * it, so a definition's {@code includeSubjectOwner} has no effect on entities until it does. An id that is
 * not a UUID, an object that has gone and a type that names nothing all answer empty — the subject does not
 * exist, and the chat is not found.
 */
@Component
public class ChatSubjectAdapter implements SubjectResolverPort {

    private final EntityObjectAccess objects;
    private final StateOperationApi states;

    public ChatSubjectAdapter(EntityObjectAccess objects, StateOperationApi states) {
        this.objects = objects;
        this.states = states;
    }

    @Override
    public Optional<SubjectInfo> resolve(String orgKey, SubjectType subjectType, String subjectDefinition, String subjectId) {
        if (subjectType != SubjectType.ENTITY) {
            return Optional.empty();
        }
        Optional<UUID> objectId = parse(subjectId);
        if (objectId.isEmpty()) {
            return Optional.empty();
        }
        try {
            EntityObjectView object = objects.find(orgKey, subjectDefinition, objectId.get());
            return Optional.of(new SubjectInfo(null, stateOf(orgKey, subjectDefinition, object)));
        } catch (EntityObjectAccessException e) {
            return Optional.empty();
        }
    }

    private String stateOf(String orgKey, String entityType, EntityObjectView object) {
        return states.stateAttributeKey(orgKey, entityType)
                .map(attribute -> object.payload().get(attribute))
                .map(String::valueOf)
                .orElse(null);
    }

    private static Optional<UUID> parse(String subjectId) {
        try {
            return subjectId == null ? Optional.empty() : Optional.of(UUID.fromString(subjectId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

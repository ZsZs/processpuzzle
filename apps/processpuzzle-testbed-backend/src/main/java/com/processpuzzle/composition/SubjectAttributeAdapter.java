package com.processpuzzle.composition;

import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.PlatformSubject;
import com.processpuzzle.event.usecase.SubjectAttributePort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Answers base-event's question whether an entity type has an attribute — what a SYSTEM event's declared parameter
 * is filled from — from base-entity's drafts, the definitions a designer saving an event works against. A subject a
 * feature declares as a {@link PlatformSubject} — a document, a calculation run — is answered from its declaration.
 */
@Component
public class SubjectAttributeAdapter implements SubjectAttributePort {

    private final EntityAttributeQuery attributeQuery;
    private final Map<String, PlatformSubject> platformSubjects;

    public SubjectAttributeAdapter(EntityAttributeQuery attributeQuery, List<PlatformSubject> platformSubjects) {
        this.attributeQuery = attributeQuery;
        this.platformSubjects = platformSubjects.stream()
                .collect(Collectors.toMap(PlatformSubject::subjectType, Function.identity(), (first, second) -> first));
    }

    @Override
    public Optional<Boolean> hasAttribute(String orgKey, String subjectType, String attributeName) {
        PlatformSubject platformSubject = platformSubjects.get(subjectType);
        if (platformSubject != null) {
            return Optional.of(platformSubject.attributes().contains(attributeName));
        }
        if (!attributeQuery.entityTypeExists(orgKey, subjectType, Channel.DRAFT)) {
            return Optional.empty();
        }
        return Optional.of(attributeQuery.attributeKind(orgKey, subjectType, attributeName, Channel.DRAFT).isPresent());
    }
}

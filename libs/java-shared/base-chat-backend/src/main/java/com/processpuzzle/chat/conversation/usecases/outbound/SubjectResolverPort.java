package com.processpuzzle.chat.conversation.usecases.outbound;

import com.processpuzzle.chat.definition.domain.SubjectType;
import java.util.Optional;

/**
 * Outbound port to whatever owns the subjects chats are bound to — base-entity for an entity instance. The
 * module never learns what a subject is; it asks whether one exists and what access decisions need to know
 * about it. The host application supplies one adapter that answers for every {@link SubjectType} it
 * supports.
 *
 * <p>Today "the caller may see the subject" amounts to "the subject exists in this organization": the
 * platform has no per-object read rights to ask about. A host that gains them answers empty for a subject
 * the caller may not see, and the chat disappears for that caller like any other hidden resource.
 */
public interface SubjectResolverPort {

    /**
     * @return the subject, or empty when it does not exist in {@code orgKey} (or the caller may not see it)
     */
    Optional<SubjectInfo> resolve(String orgKey, SubjectType subjectType, String subjectDefinition, String subjectId);
}

package com.processpuzzle.chat.conversation.usecases.outbound;

import com.processpuzzle.chat.definition.domain.SubjectType;
import java.util.Optional;

/**
 * The answer of a host that cannot resolve subjects: every subject exists, and nothing is known about it.
 * A library that cannot answer a question must not answer "no" — that would hide every chat — so this is
 * the default for a host that supplies no adapter, and the one tests use.
 */
public class PermitAllSubjectResolverPort implements SubjectResolverPort {

    @Override
    public Optional<SubjectInfo> resolve(String orgKey, SubjectType subjectType, String subjectDefinition, String subjectId) {
        return Optional.of(new SubjectInfo(null, null));
    }
}

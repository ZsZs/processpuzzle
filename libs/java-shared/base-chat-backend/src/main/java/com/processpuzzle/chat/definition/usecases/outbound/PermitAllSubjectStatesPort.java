package com.processpuzzle.chat.definition.usecases.outbound;

import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.core.definition.Channel;
import java.util.Optional;

/**
 * The answer of a host that cannot say which states a subject has: nothing is known, so nothing is refused. A
 * library that cannot answer a question must not answer "no".
 */
public class PermitAllSubjectStatesPort implements SubjectStatesPort {

    @Override
    public Optional<SubjectStates> statesOf(String orgKey, SubjectType subjectType, String subjectDefinition, Channel channel) {
        return Optional.empty();
    }
}

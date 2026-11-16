package com.processpuzzle.composition;

import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.chat.definition.usecases.outbound.SubjectStates;
import com.processpuzzle.chat.definition.usecases.outbound.SubjectStatesPort;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.state.api.StateOperationApi;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Tells base-chat which states an entity type's state machine has, from base-state, so that a release can refuse a
 * chat bound to a state nobody can reach. An entity type no machine governs answers {@link SubjectStates#NONE}.
 * ENTITY is the only subject type there is, so it always answers.
 */
@Component
public class ChatSubjectStatesAdapter implements SubjectStatesPort {

    private final StateOperationApi states;

    public ChatSubjectStatesAdapter(StateOperationApi states) {
        this.states = states;
    }

    @Override
    public Optional<SubjectStates> statesOf(String orgKey, SubjectType subjectType, String subjectDefinition, Channel channel) {
        return Optional.of(states.stateKeys(orgKey, subjectDefinition, channel).map(SubjectStates::new).orElse(SubjectStates.NONE));
    }
}

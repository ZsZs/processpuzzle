package com.processpuzzle.composition;

import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.chat.definition.usecases.outbound.SubjectStates;
import com.processpuzzle.chat.definition.usecases.outbound.SubjectStatesPort;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.state.api.StateOperationApi;
import com.processpuzzle.workflow.access.WorkflowInstanceAccess;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Tells base-chat which states an entity type's state machine has, from base-state, so that a release can refuse a
 * chat bound to a state nobody can reach. An entity type no machine governs answers {@link SubjectStates#NONE}; the
 * states of a workflow instance are the statuses base-workflow gives its instances, the same for every workflow.
 */
@Component
public class ChatSubjectStatesAdapter implements SubjectStatesPort {

    private final StateOperationApi states;
    private final WorkflowInstanceAccess workflowInstances;

    public ChatSubjectStatesAdapter(StateOperationApi states, WorkflowInstanceAccess workflowInstances) {
        this.states = states;
        this.workflowInstances = workflowInstances;
    }

    @Override
    public Optional<SubjectStates> statesOf(String orgKey, SubjectType subjectType, String subjectDefinition, Channel channel) {
        if (subjectType == SubjectType.WORKFLOW_INSTANCE) {
            return Optional.of(new SubjectStates(workflowInstances.statuses()));
        }
        return Optional.of(states.stateKeys(orgKey, subjectDefinition, channel).map(SubjectStates::new).orElse(SubjectStates.NONE));
    }
}

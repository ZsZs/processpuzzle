package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.state.api.StateOperationApi;
import com.processpuzzle.workflow.access.WorkflowInstanceAccess;
import com.processpuzzle.workflow.access.WorkflowInstanceView;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatSubjectAdapterTest {

    private final EntityObjectAccess objects = mock(EntityObjectAccess.class);
    private final StateOperationApi states = mock(StateOperationApi.class);
    private final WorkflowInstanceAccess workflowInstances = mock(WorkflowInstanceAccess.class);
    private final ChatSubjectAdapter adapter = new ChatSubjectAdapter(objects, states, workflowInstances);
    private final UUID id = UUID.randomUUID();

    @Test
    void aWorkflowInstanceIsASubjectWhoseStateIsItsStatus() {
        when(workflowInstances.find("org-1", "onboarding", id)).thenReturn(Optional.of(new WorkflowInstanceView(id, "onboarding", "COMPLETED")));

        assertThat(adapter.resolve("org-1", SubjectType.WORKFLOW_INSTANCE, "onboarding", id.toString()))
                .hasValueSatisfying(info -> assertThat(info.state()).isEqualTo("COMPLETED"));
    }

    @Test
    void anUnknownWorkflowInstanceOrANonUuidIdHasNoChat() {
        when(workflowInstances.find("org-1", "onboarding", id)).thenReturn(Optional.empty());

        assertThat(adapter.resolve("org-1", SubjectType.WORKFLOW_INSTANCE, "onboarding", id.toString())).isEmpty();
        assertThat(adapter.resolve("org-1", SubjectType.WORKFLOW_INSTANCE, "onboarding", "not-a-uuid")).isEmpty();
        assertThat(adapter.resolve("org-1", SubjectType.ENTITY, "order", null)).isEmpty();
    }

    @Test
    void anEntityIsASubjectWhoseStateIsTheMachinesAttribute() {
        when(objects.find("org-1", "order", id)).thenReturn(new EntityObjectView(id, 1L, Map.of("status", "CONFIRMED")));
        when(states.stateAttributeKey("org-1", "order")).thenReturn(Optional.of("status"));

        assertThat(adapter.resolve("org-1", SubjectType.ENTITY, "order", id.toString()))
                .hasValueSatisfying(info -> assertThat(info.state()).isEqualTo("CONFIRMED"));
    }

    @Test
    void anEntityThatHasGoneHasNoChat() {
        when(objects.find("org-1", "order", id)).thenThrow(new EntityObjectAccessException.NotFound("order", id));

        assertThat(adapter.resolve("org-1", SubjectType.ENTITY, "order", id.toString())).isEmpty();
    }
}

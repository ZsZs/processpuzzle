package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteWorkflowInstanceUseCaseTest {

    private static final String ORG = "acme";

    private final WorkflowInstanceRepository instances = mock(WorkflowInstanceRepository.class);
    private final TaskInstanceRepository tasks = mock(TaskInstanceRepository.class);
    private final EventInstanceRepository events = mock(EventInstanceRepository.class);
    private final ArtifactInstanceRepository artifacts = mock(ArtifactInstanceRepository.class);
    private final DeleteWorkflowInstanceUseCase useCase =
            new DeleteWorkflowInstanceUseCase(instances, tasks, events, artifacts);
    private final UUID id = UUID.randomUUID();

    private WorkflowInstance instance(WorkflowInstanceStatus status) {
        WorkflowInstance instance = WorkflowInstance.builder().id(id).orgKey(ORG).status(status).build();
        when(instances.findByOrgKeyAndId(ORG, id)).thenReturn(Optional.of(instance));
        return instance;
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowInstanceStatus.class, names = {"COMPLETED", "CANCELLED"})
    void erasesTheDependantsBeforeTheInstance(WorkflowInstanceStatus status) {
        WorkflowInstance instance = instance(status);

        useCase.delete(ORG, id);

        InOrder order = inOrder(events, tasks, artifacts, instances);
        order.verify(events).deleteByWorkflowInstance(ORG, id);
        order.verify(tasks).deleteByWorkflowInstance(ORG, id);
        order.verify(artifacts).deleteByWorkflowInstance(ORG, id);
        order.verify(instances).delete(instance);
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowInstanceStatus.class, names = {"ACTIVE", "SUSPENDED"})
    void refusesARunningInstanceAndTouchesNothing(WorkflowInstanceStatus status) {
        instance(status);

        assertThatThrownBy(() -> useCase.delete(ORG, id)).isInstanceOf(ConflictException.class);

        verify(events, never()).deleteByWorkflowInstance(any(), any());
        verify(tasks, never()).deleteByWorkflowInstance(any(), any());
        verify(artifacts, never()).deleteByWorkflowInstance(any(), any());
        verify(instances, never()).delete(any(WorkflowInstance.class));
    }

    @Test
    void anUnknownInstanceIsNotFound() {
        when(instances.findByOrgKeyAndId(ORG, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.delete(ORG, id)).isInstanceOf(NotFoundException.class);
    }
}

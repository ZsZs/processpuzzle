package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.ValidationException;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.TaskComment;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.usecases.outbound.ActingUserPort;
import com.processpuzzle.workflow.execution.usecases.outbound.RoleMembershipPort;
import com.processpuzzle.workflow.execution.usecases.outbound.UserNamePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Who holds a task, by name, and the notes left on it — see {@code AssignTaskUseCase} and {@link AddTaskCommentUseCase}. */
class TaskAssigneeNameAndCommentsTest {

    private static final String ORG = "org";
    private static final String TASK = "review-order";
    private static final UUID INSTANCE_ID = UUID.randomUUID();
    private static final Map<String, Object> CONTENT = Map.of("type", "doc");

    private final WorkflowInstanceRepository workflowInstances = mock(WorkflowInstanceRepository.class);
    private final ResolveWorkflowUseCase resolveWorkflow = mock(ResolveWorkflowUseCase.class);
    private final TaskInstanceRepository taskInstances = mock(TaskInstanceRepository.class);
    private final ActingUserPort principal = new ActingUserPort() {
        @Override
        public Optional<String> userId() {
            return Optional.of("user-1");
        }

        @Override
        public Optional<String> displayName() {
            return Optional.of("Ada Lovelace");
        }
    };
    private TaskInstance task;

    @BeforeEach
    void setUp() {
        task = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(INSTANCE_ID)
                .taskDefinitionId(TASK).status(TaskInstanceStatus.ACTIVE).build();
        when(taskInstances.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(ORG, INSTANCE_ID, TASK)).thenReturn(Optional.of(task));
        when(taskInstances.save(any(TaskInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowInstances.findByOrgKeyAndId(ORG, INSTANCE_ID))
                .thenReturn(Optional.of(WorkflowInstance.builder().id(INSTANCE_ID).orgKey(ORG).workflowId("wf").build()));
        ResolvedWorkflow workflow = mock(ResolvedWorkflow.class);
        when(workflow.findTask(TASK)).thenReturn(Optional.of(new ResolvedWorkflow.ResolvedTask(
                TaskUse.builder().taskDefinitionId(TASK).build(), TaskDefinition.builder().id(TASK).build())));
        when(workflow.findRole(any())).thenReturn(Optional.empty());
        when(resolveWorkflow.resolveByOrgKeyAndId(ORG, "wf")).thenReturn(workflow);
    }

    @Test
    void aUserClaimingForThemselvesIsNamedFromTheirToken() {
        assertThat(assignUseCase(null).assign(ORG, INSTANCE_ID, TASK, "user-1").getAssigneeName()).isEqualTo("Ada Lovelace");
    }

    @Test
    void theDirectoryNamesSomeoneElse() {
        UserNamePort directory = (orgKey, userId) -> Optional.of("Grace Hopper");

        assertThat(assignUseCase(directory).assign(ORG, INSTANCE_ID, TASK, "user-2").getAssigneeName()).isEqualTo("Grace Hopper");
    }

    @Test
    void someoneElseWithoutADirectoryIsLeftUnnamedRatherThanGivenTheCallersName() {
        assertThat(assignUseCase(null).assign(ORG, INSTANCE_ID, TASK, "user-2").getAssigneeName()).isNull();
    }

    @Test
    void aCommentIsStampedWithTheCallerAndAppended() {
        AddTaskCommentUseCase useCase = new AddTaskCommentUseCase(taskInstances, provider(principal));

        useCase.addComment(ORG, INSTANCE_ID, TASK, CONTENT);
        TaskInstance commented = useCase.addComment(ORG, INSTANCE_ID, TASK, Map.of("type", "doc", "second", true));

        assertThat(commented.getComments()).hasSize(2);
        TaskComment first = commented.getComments().getFirst();
        assertThat(first.getAuthor()).isEqualTo("Ada Lovelace");
        assertThat(first.getAuthorId()).isEqualTo("user-1");
        assertThat(first.getCreatedAt()).isNotNull();
        assertThat(first.getContent()).isEqualTo(CONTENT);
    }

    @Test
    void aFinishedTaskTakesNoComment() {
        task.setStatus(TaskInstanceStatus.COMPLETED);
        AddTaskCommentUseCase useCase = new AddTaskCommentUseCase(taskInstances, provider(principal));

        assertThatThrownBy(() -> useCase.addComment(ORG, INSTANCE_ID, TASK, CONTENT)).isInstanceOf(ConflictException.class);
    }

    @Test
    void anEmptyCommentIsRefused() {
        AddTaskCommentUseCase useCase = new AddTaskCommentUseCase(taskInstances, provider(principal));

        assertThatThrownBy(() -> useCase.addComment(ORG, INSTANCE_ID, TASK, Map.of())).isInstanceOf(ValidationException.class);
    }

    @Test
    void aTaskStoredBeforeCommentsExistedReadsAsHavingNone() {
        task.setComments(null);

        assertThat(task.getComments()).isEmpty();
    }

    private AssignTaskUseCase assignUseCase(UserNamePort directory) {
        ObjectProvider<RoleMembershipPort> roles = provider(null);
        return new AssignTaskUseCase(workflowInstances, resolveWorkflow, taskInstances, roles, provider(directory), provider(principal));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique()).thenReturn(bean);
        when(provider.getIfAvailable()).thenReturn(bean);
        when(provider.getIfUnique(any())).thenAnswer(invocation -> bean != null ? bean
                : ((java.util.function.Supplier<T>) invocation.getArgument(0)).get());
        return provider;
    }
}

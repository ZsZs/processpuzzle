package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.common.ValidationException;
import com.processpuzzle.workflow.execution.domain.TaskComment;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.usecases.outbound.ActingUserPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Appends one comment to an ACTIVE task — a note made while the work is being done, kept with the task.
 *
 * <p><b>Append-only by construction.</b> The request carries content alone; there is no operation that takes a
 * history, so nothing can rewrite or drop an earlier entry, and base-entity's {@code CommentHistoryGuard} has no
 * counterpart here. Author and time are stamped from {@link ActingUserPort} rather than accepted from the request.
 *
 * <p>Any caller may comment, not only the assignee: a colleague's note on someone else's task is the point of
 * leaving one. A finished task takes none — its history is its record.
 */
@Component
@Transactional
public class AddTaskCommentUseCase {

    static final String UNKNOWN_AUTHOR = "Unknown";

    private final TaskInstanceRepository taskInstanceRepository;
    private final ObjectProvider<ActingUserPort> actingUser;

    public AddTaskCommentUseCase(TaskInstanceRepository taskInstanceRepository, ObjectProvider<ActingUserPort> actingUser) {
        this.taskInstanceRepository = taskInstanceRepository;
        this.actingUser = actingUser;
    }

    public TaskInstance addComment(String orgKey, UUID workflowInstanceId, String taskDefinitionId, Map<String, Object> content) {
        if (content == null || content.isEmpty()) {
            throw new ValidationException("A comment needs content");
        }
        TaskInstance taskInstance = taskInstanceRepository
                .findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(orgKey, workflowInstanceId, taskDefinitionId)
                .orElseThrow(() -> new NotFoundException(
                        "No task '%s' in workflow instance '%s'".formatted(taskDefinitionId, workflowInstanceId)));
        if (taskInstance.getStatus() != TaskInstanceStatus.ACTIVE) {
            throw new ConflictException("Task '%s' is %s, not ACTIVE — cannot comment".formatted(taskDefinitionId, taskInstance.getStatus()));
        }

        Optional<ActingUserPort> caller = Optional.ofNullable(actingUser.getIfUnique());
        taskInstance.getComments().add(TaskComment.builder()
                .author(caller.flatMap(ActingUserPort::displayName).orElse(UNKNOWN_AUTHOR))
                .authorId(caller.flatMap(ActingUserPort::userId).orElse(null))
                .createdAt(Instant.now())
                .content(content)
                .build());
        return taskInstanceRepository.save(taskInstance);
    }
}

package com.processpuzzle.workflow.execution.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * One note left on a {@link TaskInstance} while it was ACTIVE. Stored as a JSONB list on
 * {@link TaskInstance#getComments()}, like {@link StepResult}, and in the shape base-entity's COMMENTS
 * attribute stores — so the frontend renders a task's comments with the same control.
 *
 * <p>Author and time are the server's: {@code AddTaskCommentUseCase} takes them from the caller's token, never
 * from the request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskComment {
    private String author;
    private String authorId;
    private Instant createdAt;
    /** Rich text, as the editor's JSON document. */
    private Map<String, Object> content;
}

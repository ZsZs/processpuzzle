package com.processpuzzle.workflow.definition.adapters.inbound.release;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Releases task definitions. A task refers to the roles that perform it, the artifacts it reads and writes, the rules
 * of its pre- and postcondition and the tools its steps call.
 *
 * <p><strong>What it refuses.</strong> Removing a task that a running workflow has open: its task instance would name
 * a definition the running application no longer has, so it could be neither assigned nor completed.
 */
@Component
public class TaskReleaseParticipant extends DefinitionReleaseParticipant<TaskDefinition> {

    /** A task instance not yet finished: the ones a removed definition would strand. */
    static final Set<TaskInstanceStatus> OPEN = EnumSet.of(TaskInstanceStatus.PENDING, TaskInstanceStatus.ACTIVE,
            TaskInstanceStatus.BLOCKED);

    private final TaskDefinitionRepository repository;
    private final TaskInstanceRepository taskInstances;

    public TaskReleaseParticipant(TaskDefinitionRepository repository, TaskInstanceRepository taskInstances) {
        super(DefinitionKinds.TASK, DefinitionKinds.TASK_ORDER, repository);
        this.repository = repository;
        this.taskInstances = taskInstances;
    }

    @Override
    protected List<TaskDefinition> rows(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannel(orgKey, channel);
    }

    @Override
    protected String keyOf(TaskDefinition row) {
        return row.getId();
    }

    @Override
    protected Map<String, Object> contentOf(TaskDefinition row) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", row.getName());
        content.put("description", row.getDescription());
        content.put("performedByRoles", row.getPerformedByRoles());
        content.put("inputs", row.getInputs());
        content.put("outputs", row.getOutputs());
        content.put("preconditionRuleId", row.getPreconditionRuleId());
        content.put("postconditionRuleId", row.getPostconditionRuleId());
        content.put("steps", row.getSteps());
        return content;
    }

    @Override
    protected TaskDefinition blankLike(TaskDefinition draft) {
        return TaskDefinition.builder().orgKey(draft.getOrgKey()).id(draft.getId()).build();
    }

    @Override
    protected void copy(TaskDefinition draft, TaskDefinition released) {
        released.setName(draft.getName());
        released.setDescription(draft.getDescription());
        released.setPerformedByRoles(copyOf(draft.getPerformedByRoles()));
        released.setInputs(copyOf(draft.getInputs()));
        released.setOutputs(copyOf(draft.getOutputs()));
        released.setPreconditionRuleId(draft.getPreconditionRuleId());
        released.setPostconditionRuleId(draft.getPostconditionRuleId());
        released.setSteps(copyOf(draft.getSteps()));
    }

    @Override
    protected List<DefinitionReference> referencesOf(TaskDefinition draft) {
        List<DefinitionReference> references = new ArrayList<>();
        addAll(references, draft, DefinitionKinds.ROLE, draft.getPerformedByRoles());
        addAll(references, draft, DefinitionKinds.ARTIFACT, draft.getInputs());
        addAll(references, draft, DefinitionKinds.ARTIFACT, draft.getOutputs());
        addAll(references, draft, DefinitionKinds.RULE, List.of(nullToBlank(draft.getPreconditionRuleId()),
                nullToBlank(draft.getPostconditionRuleId())));
        if (draft.getSteps() != null) {
            addAll(references, draft, DefinitionKinds.TOOL, draft.getSteps().stream().map(StepDefinition::getToolDefinitionId).toList());
        }
        return references.stream().distinct().toList();
    }

    @Override
    protected List<ReleaseProblem> problemsOf(String orgKey, Map<String, TaskDefinition> drafts,
                                              Map<String, TaskDefinition> released) {
        return released.keySet().stream()
                .filter(taskId -> !drafts.containsKey(taskId))
                .filter(taskId -> taskInstances.existsByOrgKeyAndTaskDefinitionIdAndStatusIn(orgKey, taskId, OPEN))
                .map(taskId -> ReleaseProblem.error(kind(), taskId, "workflow.release.taskRemovedWhileOpen",
                        "The task '%s' is removed, but a running workflow has it open.".formatted(taskId)))
                .toList();
    }

    private void addAll(List<DefinitionReference> references, TaskDefinition draft, String toKind, List<String> keys) {
        if (keys != null) {
            keys.stream().filter(ArtifactReleaseParticipant::isNamed)
                    .forEach(key -> references.add(DefinitionReference.required(kind(), draft.getId(), toKind, key)));
        }
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    static <T> List<T> copyOf(List<T> list) {
        return list == null ? null : new ArrayList<>(list);
    }
}

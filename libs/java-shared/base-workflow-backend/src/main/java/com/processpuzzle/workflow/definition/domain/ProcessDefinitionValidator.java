package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.workflow.common.ValidationException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Referential integrity within a single {@link ProcessDefinition}: role/work-product/task ids
 * unique within the process, every task's {@code performedBy} names a role that exists in the
 * same process, and every task's {@code dependsOn} names a task that exists in the same process
 * and isn't itself.
 */
@Component
public class ProcessDefinitionValidator {

    public void validate(ProcessDefinition process) {
        requireUnique(process.getRoles().stream().map(RoleDefinition::getId).toList(), "role");
        requireUnique(process.getWorkProducts().stream().map(WorkProductDefinition::getId).toList(), "work product");
        requireUnique(process.getTasks().stream().map(TaskDefinition::getId).toList(), "task");

        Set<String> roleIds = process.getRoles().stream().map(RoleDefinition::getId).collect(Collectors.toSet());
        Set<String> taskIds = process.getTasks().stream().map(TaskDefinition::getId).collect(Collectors.toSet());

        for (TaskDefinition task : process.getTasks()) {
            if (!roleIds.contains(task.getPerformedBy())) {
                throw new ValidationException(
                        "Task '%s' is performedBy unknown role '%s'".formatted(task.getId(), task.getPerformedBy()));
            }
            for (String dependsOnId : task.getDependsOn()) {
                if (dependsOnId.equals(task.getId())) {
                    throw new ValidationException("Task '%s' cannot depend on itself".formatted(task.getId()));
                }
                if (!taskIds.contains(dependsOnId)) {
                    throw new ValidationException(
                            "Task '%s' dependsOn unknown task '%s'".formatted(task.getId(), dependsOnId));
                }
            }
        }
    }

    private void requireUnique(java.util.List<String> ids, String kind) {
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            if (!seen.add(id)) {
                throw new ValidationException("Duplicate %s id '%s' within process".formatted(kind, id));
            }
        }
    }
}

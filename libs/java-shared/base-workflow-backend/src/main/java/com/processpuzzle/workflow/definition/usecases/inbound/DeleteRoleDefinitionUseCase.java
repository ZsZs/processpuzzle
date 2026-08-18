package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteRoleDefinitionUseCase {

    private final ProcessDefinitionRepository repository;

    public void delete(String orgKey, String processId, String roleId) {
        ProcessDefinition process = repository.findByOrgKeyAndId(orgKey, processId)
                .orElseThrow(() -> new NotFoundException("No process definition with id '%s'".formatted(processId)));

        RoleDefinition role = process.findRole(roleId)
                .orElseThrow(() -> new NotFoundException(
                        "No role '%s' in process '%s'".formatted(roleId, processId)));

        java.util.List<String> stillPerformedBy = process.getTasks().stream()
                .filter(t -> t.getPerformedBy().equals(roleId))
                .map(TaskDefinition::getId)
                .toList();
        if (!stillPerformedBy.isEmpty()) {
            throw new ConflictException(
                    "Role '%s' is still performedBy tasks %s".formatted(roleId, stillPerformedBy));
        }
        process.getRoles().remove(role);
        repository.save(process);
    }
}

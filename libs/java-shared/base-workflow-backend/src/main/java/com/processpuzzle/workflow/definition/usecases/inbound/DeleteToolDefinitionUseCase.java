package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteToolDefinitionUseCase {

    private final ToolDefinitionRepository toolRepository;
    private final ProcessDefinitionRepository processRepository;

    public void delete(String orgKey, String id) {
        ToolDefinition tool = toolRepository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No tool definition with id '%s'".formatted(id)));

        // tools is a JSONB list on ProcessDefinition, not a foreign key, so this is an in-memory
        // scan rather than a query. Fine for a catalog-sized number of process definitions per
        // org; revisit with a native jsonb containment query if that stops being true.
        java.util.List<String> stillReferencedBy = processRepository.findByOrgKey(orgKey).stream()
                .filter(p -> p.getTools().contains(id))
                .map(ProcessDefinition::getId)
                .toList();
        if (!stillReferencedBy.isEmpty()) {
            throw new ConflictException("Tool '%s' is still referenced by processes %s".formatted(id, stillReferencedBy));
        }
        toolRepository.delete(tool);
    }
}

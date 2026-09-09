package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class CreateToolDefinitionUseCase {

    private final ToolDefinitionRepository repository;

    public ToolDefinition create(String orgKey, ToolDefinition tool) {
        tool.setOrgKey(orgKey);
        if (repository.existsByOrgKeyAndId(orgKey, tool.getId())) {
            throw new ConflictException("Tool definition '%s' already exists".formatted(tool.getId()));
        }
        return repository.save(tool);
    }
}

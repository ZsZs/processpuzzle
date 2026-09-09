package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class ReplaceToolDefinitionUseCase {

    private final ToolDefinitionRepository repository;

    public ToolDefinition replace(String orgKey, String id, ToolDefinition desiredState) {
        ToolDefinition existing = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No tool definition with id '%s'".formatted(id)));

        if (desiredState.getVersion() != null && !desiredState.getVersion().equals(existing.getVersion())) {
            throw new ConflictException(
                    "Tool definition '%s' was modified concurrently — reload and retry".formatted(id));
        }

        existing.setName(desiredState.getName());
        existing.setDescription(desiredState.getDescription());
        existing.setBaseUrl(desiredState.getBaseUrl());
        existing.setAuth(desiredState.getAuth());
        existing.setOperations(desiredState.getOperations());
        return repository.save(existing);
    }
}

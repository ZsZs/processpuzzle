package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FindToolDefinitionUseCase {

    private final ToolDefinitionRepository repository;

    @Transactional(readOnly = true)
    public ToolDefinition findByOrgKeyAndId(String orgKey, String id, Channel channel) {
        return repository.findByOrgKeyAndIdAndChannel(orgKey, id, channel)
                .orElseThrow(() -> new NotFoundException("No tool definition with id '%s'".formatted(id)));
    }
}

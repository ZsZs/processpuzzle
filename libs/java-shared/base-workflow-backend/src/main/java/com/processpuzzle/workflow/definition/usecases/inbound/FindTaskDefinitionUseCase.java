package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FindTaskDefinitionUseCase {

    private final TaskDefinitionRepository repository;

    @Transactional(readOnly = true)
    public TaskDefinition findByOrgKeyAndId(String orgKey, String id, Channel channel) {
        return repository.findByOrgKeyAndIdAndChannel(orgKey, id, channel)
                .orElseThrow(() -> new NotFoundException("No task definition with id '%s'".formatted(id)));
    }
}

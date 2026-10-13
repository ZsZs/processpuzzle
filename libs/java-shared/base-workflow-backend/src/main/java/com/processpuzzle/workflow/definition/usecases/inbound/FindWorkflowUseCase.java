package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FindWorkflowUseCase {

    private final WorkflowRepository repository;

    @Transactional(readOnly = true)
    public Workflow findByOrgKeyAndId(String orgKey, String id, Channel channel) {
        return repository.findByOrgKeyAndIdAndChannel(orgKey, id, channel)
                .orElseThrow(() -> new NotFoundException("No workflow definition with id '%s'".formatted(id)));
    }
}

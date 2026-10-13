package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FindArtifactDefinitionUseCase {

    private final ArtifactDefinitionRepository repository;

    @Transactional(readOnly = true)
    public ArtifactDefinition findByOrgKeyAndId(String orgKey, String id, Channel channel) {
        return repository.findByOrgKeyAndIdAndChannel(orgKey, id, channel)
                .orElseThrow(() -> new NotFoundException("No artifact definition with id '%s'".formatted(id)));
    }
}

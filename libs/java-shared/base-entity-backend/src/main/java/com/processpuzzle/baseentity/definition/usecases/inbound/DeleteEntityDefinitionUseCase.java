package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.usecases.outbound.EntityInstanceExistenceCheckPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteEntityDefinitionUseCase {

    private final EntityDefinitionRepository repository;
    private final EntityInstanceExistenceCheckPort instanceExistenceCheckPort;

    public void delete(String orgKey, String code) {
        BaseEntityDefinition definition = repository.findByOrgKeyAndCodeAndChannel(orgKey, code, Channel.DRAFT)
            .orElseThrow(() -> new NotFoundException("No entity definition with code '%s'".formatted(code)));

        if (instanceExistenceCheckPort.existsAnyInstanceOf(orgKey, code)) {
            throw new ConflictException("'%s' still has instances — delete them first".formatted(code));
        }
        boolean isComponentParent = repository.findAllByOrgKeyAndChannel(orgKey, Channel.DRAFT).stream()
            .anyMatch(candidate -> candidate.getComponentParents().contains(code));
        if (isComponentParent) {
            throw new ConflictException("'%s' is still declared as a componentParent by another definition".formatted(code));
        }
        repository.delete(definition);
    }
}

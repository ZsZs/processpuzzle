package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FindEntityDefinitionByCodeUseCase {

    private final EntityDefinitionRepository repository;

    @Transactional(readOnly = true)
    public BaseEntityDefinition findByCode(String orgKey, String code) {
        return repository.findByOrgKeyAndCode(orgKey, code)
            .orElseThrow(() -> new NotFoundException("No entity definition with code '%s'".formatted(code)));
    }
}

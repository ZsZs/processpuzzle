package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The definitions of one organization whose codes are among {@code codes}; unknown codes are skipped. */
@Component
@RequiredArgsConstructor
public class FindEntityDefinitionsByCodesUseCase {

    private final EntityDefinitionRepository repository;

    @Transactional(readOnly = true)
    public List<BaseEntityDefinition> find(String orgKey, Set<String> codes) {
        return repository.findAllByOrgKey(orgKey).stream()
            .filter(definition -> codes.contains(definition.getCode()))
            .toList();
    }
}

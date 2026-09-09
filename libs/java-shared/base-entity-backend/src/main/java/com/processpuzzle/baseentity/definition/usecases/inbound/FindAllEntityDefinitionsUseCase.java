package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FindAllEntityDefinitionsUseCase {

    private final EntityDefinitionRepository repository;

    @Transactional(readOnly = true)
    public Page<BaseEntityDefinition> findAll(EntityDefinitionStatus status, Boolean isEmbedded, Pageable pageable) {
        Specification<BaseEntityDefinition> specification = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (isEmbedded != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("isEmbedded"), isEmbedded));
        }
        return repository.findAll(specification, pageable);
    }
}

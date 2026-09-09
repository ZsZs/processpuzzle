package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.usecases.outbound.RsqlToInstanceSpecificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SearchEntityInstancesUseCase {

    private final EntityObjectRepository repository;
    private final RsqlToInstanceSpecificationPort rsqlSpecificationPort;

    @Transactional(readOnly = true)
    public Page<EntityObject> search(String entityDefinitionCode, String rsql, Pageable pageable) {
        Specification<EntityObject> specification = rsqlSpecificationPort
            .toSpecification(rsql, entityDefinitionCode)
            .and((root, query, cb) -> cb.equal(root.get("entityDefinitionCode"), entityDefinitionCode));
        return repository.findAll(specification, pageable);
    }
}

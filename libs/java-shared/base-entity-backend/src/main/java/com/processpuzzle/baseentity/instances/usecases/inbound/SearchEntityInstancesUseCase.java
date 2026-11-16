package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
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
    private final EntityObjectScope scope;
    private final RsqlToInstanceSpecificationPort rsqlSpecificationPort;

    @Transactional(readOnly = true)
    public Page<EntityObject> search(String orgKey, String entityDefinitionCode, String rsql, Pageable pageable) {
        return search(orgKey, entityDefinitionCode, rsql, pageable, false);
    }

    /** @param includeRetired the retired objects of a shared type too — for the common's members reinstating one */
    @Transactional(readOnly = true)
    public Page<EntityObject> search(String orgKey, String entityDefinitionCode, String rsql, Pageable pageable, boolean includeRetired) {
        String storageOrgKey = scope.storageOrgKey(orgKey, entityDefinitionCode);
        Specification<EntityObject> specification = rsqlSpecificationPort
            .toSpecification(orgKey, rsql, entityDefinitionCode)
            .and((root, query, cb) -> cb.equal(root.get("orgKey"), storageOrgKey))
            .and((root, query, cb) -> cb.equal(root.get("entityDefinitionCode"), entityDefinitionCode))
            .and((root, query, cb) -> includeRetired && storageOrgKey.equals(orgKey) ? cb.conjunction() : cb.isNull(root.get("retiredAt")));
        return repository.findAll(specification, pageable);
    }
}

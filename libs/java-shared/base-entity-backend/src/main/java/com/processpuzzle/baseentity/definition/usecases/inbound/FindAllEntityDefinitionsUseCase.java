package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionStatus;
import com.processpuzzle.baseentity.definition.domain.SharedTypes;
import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FindAllEntityDefinitionsUseCase {

    private final EntityDefinitionRepository repository;
    private final ObjectProvider<DataSpaceDirectory> dataSpaces;

    @Transactional(readOnly = true)
    public Page<BaseEntityDefinition> findAll(String orgKey, Channel channel, EntityDefinitionStatus status, Boolean isEmbedded,
                                              Pageable pageable) {
        Specification<BaseEntityDefinition> specification = (root, query, cb) ->
            cb.and(cb.equal(root.get("orgKey"), orgKey), cb.equal(root.get("channel"), channel));
        // A member also sees the types its common shares, as released: it reads them but never drafts them.
        String common = SharedTypes.commonOf(dataSpaces, orgKey).orElse(null);
        if (common != null) {
            specification = specification.or((root, query, cb) -> cb.and(
                cb.equal(root.get("orgKey"), common), cb.equal(root.get("channel"), Channel.RELEASED),
                cb.isNotNull(root.get("sharingWrite"))));
        }
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (isEmbedded != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("isEmbedded"), isEmbedded));
        }
        return repository.findAll(specification, pageable);
    }
}

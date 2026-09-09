package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class FindAllArtifactDefinitionsUseCase {

    private final ArtifactDefinitionRepository repository;
    private final RsqlSpecificationBuilder<ArtifactDefinition> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllArtifactDefinitionsUseCase(ArtifactDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * Not paged, for the reason {@link FindAllToolDefinitionsUseCase} is not: the catalog list
     * operations of base-workflow-api.yaml return a plain array, these being small,
     * catalog-sized resources per organization.
     */
    public List<ArtifactDefinition> findAll(String orgKey, String where, String order) {
        Specification<ArtifactDefinition> spec = (root, query, cb) -> cb.equal(root.get("orgKey"), orgKey);

        Specification<ArtifactDefinition> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(order);
        return repository.findAll(spec, sort);
    }
}

package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class FindAllToolDefinitionsUseCase {

    private final ToolDefinitionRepository repository;
    private final RsqlSpecificationBuilder<ToolDefinition> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllToolDefinitionsUseCase(ToolDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * Not paged, unlike workflow definitions — ToolDefinitionsApi's list operation returns a
     * plain array (see base-workflow-api.yaml), consistent with tools being a small,
     * catalog-sized resource per organization.
     */
    public List<ToolDefinition> findAll(String orgKey, String where, String order) {
        Specification<ToolDefinition> spec = (root, query, cb) -> cb.equal(root.get("orgKey"), orgKey);

        Specification<ToolDefinition> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(order);
        return repository.findAll(spec, sort);
    }
}

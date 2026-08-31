package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class FindAllTaskDefinitionsUseCase {

    private final TaskDefinitionRepository repository;
    private final RsqlSpecificationBuilder<TaskDefinition> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllTaskDefinitionsUseCase(TaskDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * Not paged, for the reason {@link FindAllToolDefinitionsUseCase} is not: the catalog list
     * operations of base-workflow-api.yaml return a plain array, these being small,
     * catalog-sized resources per organization.
     */
    public List<TaskDefinition> findAll(String orgKey, String where, String order) {
        Specification<TaskDefinition> spec = (root, query, cb) -> cb.equal(root.get("orgKey"), orgKey);

        Specification<TaskDefinition> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(order);
        return repository.findAll(spec, sort);
    }
}

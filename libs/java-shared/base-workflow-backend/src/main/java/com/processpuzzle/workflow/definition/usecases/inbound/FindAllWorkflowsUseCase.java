package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class FindAllWorkflowsUseCase {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final WorkflowRepository repository;
    private final RsqlSpecificationBuilder<Workflow> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllWorkflowsUseCase(WorkflowRepository repository) {
        this.repository = repository;
    }

    /**
     * The tenant specification is ANDed first and is never optional -- RSQL permits a top-level
     * OR, which would otherwise let {@code where} escape the org filter. Same guard as
     * {@code FindAllRules} in base-rule-backend.
     */
    public Page<Workflow> findAll(String orgKey, String where, String order, Integer page, Integer size) {
        Specification<Workflow> spec = (root, query, cb) -> cb.equal(root.get("orgKey"), orgKey);

        Specification<Workflow> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(order);
        Pageable pageable = PageRequest.of(page != null ? page : DEFAULT_PAGE, size != null ? size : DEFAULT_SIZE, sort);
        return repository.findAll(spec, pageable);
    }
}

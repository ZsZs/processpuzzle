package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagram;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagramRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the organization's diagram layouts, same shape as {@link FindAllWorkflowsUseCase}.
 *
 * <p>Only the scalar columns are meaningfully filterable: {@code nodes} and {@code edges} are
 * serialized JSON, so an RSQL predicate naming one would not mean anything useful.
 *
 * <p>The tenant specification is ANDed first and is never optional — RSQL permits a top-level OR, which
 * would otherwise let {@code where} escape the org filter. Same guard as {@link FindAllWorkflowsUseCase}.
 */
@Component
@Transactional(readOnly = true)
public class FindAllWorkflowDiagramsUseCase {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final WorkflowDiagramRepository repository;
    private final RsqlSpecificationBuilder<WorkflowDiagram> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllWorkflowDiagramsUseCase(WorkflowDiagramRepository repository) {
        this.repository = repository;
    }

    public Page<WorkflowDiagram> findAll(String orgKey, String where, String order, Integer page, Integer size) {
        Specification<WorkflowDiagram> spec = (root, query, cb) -> cb.equal(root.get("orgKey"), orgKey);

        Specification<WorkflowDiagram> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(order);
        Pageable pageable = PageRequest.of(page != null ? page : DEFAULT_PAGE, size != null ? size : DEFAULT_SIZE, sort);
        return repository.findAll(spec, pageable);
    }
}

package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class FindAllWorkflowInstancesUseCase {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final WorkflowInstanceRepository repository;
    private final RsqlSpecificationBuilder<WorkflowInstance> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllWorkflowInstancesUseCase(WorkflowInstanceRepository repository) {
        this.repository = repository;
    }

    public record Query(
            String orgKey,
            String workflowId,
            WorkflowInstanceStatus status,
            String entityId,
            String where,
            String order,
            Integer page,
            Integer size
    ) {}

    public Page<WorkflowInstance> findAll(Query query) {
        Specification<WorkflowInstance> spec = (root, q, cb) -> cb.equal(root.get("orgKey"), query.orgKey());

        if (query.workflowId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("workflowId"), query.workflowId()));
        }
        if (query.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), query.status()));
        }
        if (query.entityId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityId"), query.entityId()));
        }
        Specification<WorkflowInstance> whereSpec = rsqlBuilder.build(query.where());
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(query.order());
        Pageable pageable = PageRequest.of(query.page() != null ? query.page() : DEFAULT_PAGE,
                query.size() != null ? query.size() : DEFAULT_SIZE, sort);
        return repository.findAll(spec, pageable);
    }
}

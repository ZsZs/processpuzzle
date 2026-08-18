package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.workflow.execution.domain.ProcessInstance;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceRepository;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class FindAllProcessInstancesUseCase {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final ProcessInstanceRepository repository;
    private final RsqlSpecificationBuilder<ProcessInstance> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllProcessInstancesUseCase(ProcessInstanceRepository repository) {
        this.repository = repository;
    }

    public record Query(
            String orgKey,
            String processDefinitionId,
            ProcessInstanceStatus status,
            String entityId,
            String where,
            String order,
            Integer page,
            Integer size
    ) {}

    public Page<ProcessInstance> findAll(Query query) {
        Specification<ProcessInstance> spec = (root, q, cb) -> cb.equal(root.get("orgKey"), query.orgKey());

        if (query.processDefinitionId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("processDefinitionId"), query.processDefinitionId()));
        }
        if (query.status() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), query.status()));
        }
        if (query.entityId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityId"), query.entityId()));
        }
        Specification<ProcessInstance> whereSpec = rsqlBuilder.build(query.where());
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(query.order());
        Pageable pageable = PageRequest.of(query.page() != null ? query.page() : DEFAULT_PAGE,
                query.size() != null ? query.size() : DEFAULT_SIZE, sort);
        return repository.findAll(spec, pageable);
    }
}

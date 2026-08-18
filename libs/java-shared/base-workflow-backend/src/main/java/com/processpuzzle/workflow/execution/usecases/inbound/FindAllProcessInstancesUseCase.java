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

    public Page<ProcessInstance> findAll(String orgKey, String processDefinitionId, ProcessInstanceStatus status,
                                          String entityId, String where, String order, Integer page, Integer size) {
        Specification<ProcessInstance> spec = (root, query, cb) -> cb.equal(root.get("orgKey"), orgKey);

        if (processDefinitionId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("processDefinitionId"), processDefinitionId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (entityId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        Specification<ProcessInstance> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(order);
        Pageable pageable = PageRequest.of(page != null ? page : DEFAULT_PAGE, size != null ? size : DEFAULT_SIZE, sort);
        return repository.findAll(spec, pageable);
    }
}

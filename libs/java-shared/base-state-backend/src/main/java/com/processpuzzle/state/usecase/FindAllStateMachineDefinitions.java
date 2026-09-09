package com.processpuzzle.state.usecase;

import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindAllStateMachineDefinitions {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final StateMachineDefinitionRepository repository;
    private final RsqlSpecificationBuilder<StateMachineDefinition> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllStateMachineDefinitions(StateMachineDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * The tenant specification is ANDed <em>first</em> and is never optional — same discipline as
     * {@code FindAllRules}, for the same reason: RSQL permits a top-level {@code OR}, which would
     * otherwise escape the filter and return other organizations' definitions.
     */
    public Page<StateMachineDefinition> execute(String orgKey, String where, String order, Integer page, Integer size) {
        Specification<StateMachineDefinition> spec = orgKeySpec(orgKey);
        Specification<StateMachineDefinition> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(order);
        Pageable pageable = PageRequest.of(page != null ? page : DEFAULT_PAGE, size != null ? size : DEFAULT_SIZE, sort);
        return repository.findAll(spec, pageable);
    }

    private static Specification<StateMachineDefinition> orgKeySpec(String orgKey) {
        return (root, query, cb) -> cb.equal(root.get("orgKey"), orgKey);
    }
}

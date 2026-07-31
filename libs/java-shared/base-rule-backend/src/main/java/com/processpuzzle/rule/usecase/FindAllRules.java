package com.processpuzzle.rule.usecase;

import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindAllRules {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final RuleDefinitionRepository repository;
    private final RsqlSpecificationBuilder<RuleDefinition> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllRules(RuleDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * The tenant specification is ANDed <em>first</em> and is never optional. RSQL permits a
     * top-level {@code OR}, which would otherwise escape the filter and return other
     * organizations' rules — so there is deliberately no unfiltered {@code findAll} branch here.
     */
    public Page<RuleDefinition> execute(String orgKey, String context, String where,
                                        String order, Integer page, Integer size) {
        Specification<RuleDefinition> spec = orgKeySpec(orgKey);
        Specification<RuleDefinition> contextSpec = contextSpec(context);
        if (contextSpec != null) {
            spec = spec.and(contextSpec);
        }
        Specification<RuleDefinition> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(order);
        Pageable pageable = PageRequest.of(page != null ? page : DEFAULT_PAGE, size != null ? size : DEFAULT_SIZE, sort);
        return repository.findAll(spec, pageable);
    }

    private static Specification<RuleDefinition> orgKeySpec(String orgKey) {
        return (root, query, cb) -> cb.equal(root.get("orgKey"), orgKey);
    }

    private static Specification<RuleDefinition> contextSpec(String context) {
        if (context == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("context"), context);
    }
}

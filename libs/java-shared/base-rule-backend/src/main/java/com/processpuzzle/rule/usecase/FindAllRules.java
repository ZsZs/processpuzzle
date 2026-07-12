package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.adapter.inbound.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.rule.adapter.inbound.rsql.SortParser;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FindAllRules {

    private final RuleDefinitionRepository repository;
    private final RsqlSpecificationBuilder<RuleDefinition> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllRules(RuleDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<RuleDefinition> execute(String context, String where, String order) {
        Specification<RuleDefinition> spec = contextSpec(context);
        Specification<RuleDefinition> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec == null ? whereSpec : spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(order);
        return repository.findAll(spec, sort);
    }

    private static Specification<RuleDefinition> contextSpec(String context) {
        if (context == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("context"), context);
    }
}

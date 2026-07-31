package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.core.rsql.SortParser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists an organization's app definitions.
 *
 * <p>The organization specification is always applied and the caller's RSQL is ANDed onto it, never
 * the other way round. RSQL supports a top-level {@code ,} (OR), so composing in the other order
 * would let a crafted {@code where} widen the result set past the tenant boundary; {@code and} can
 * only narrow.
 *
 * <p>Both {@code orgKey} and {@code id} are flat entity attributes despite being a composite key,
 * so RSQL selectors are the plain names a client would expect ({@code id=="claims-app"}) rather
 * than a nested path.
 */
@Service
@Transactional(readOnly = true)
public class FindAllAppDefinitions {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final AppDefinitionRepository repository;
    private final OrganizationGuard guard;
    private final RsqlSpecificationBuilder<AppDefinition> rsqlBuilder = new RsqlSpecificationBuilder<>();

    public FindAllAppDefinitions(AppDefinitionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public Page<AppDefinition> execute(String orgKey, String where, String order, Integer page, Integer size) {
        guard.requireAccess(orgKey);

        Specification<AppDefinition> spec = orgKeySpec(orgKey);
        Specification<AppDefinition> whereSpec = rsqlBuilder.build(where);
        if (whereSpec != null) {
            spec = spec.and(whereSpec);
        }
        Sort sort = SortParser.parse(order);
        Pageable pageable = PageRequest.of(
                page != null ? page : DEFAULT_PAGE,
                size != null ? size : DEFAULT_SIZE,
                sort);
        return repository.findAll(spec, pageable);
    }

    private static Specification<AppDefinition> orgKeySpec(String orgKey) {
        return (root, query, cb) -> cb.equal(root.get("orgKey"), orgKey);
    }
}
